/*
 *
 *  Copyright 2024-Present Alan Littleford
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package com.mentalresonance.dust.core.net;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.mentalresonance.dust.core.actors.ActorSystem;
import com.mentalresonance.dust.core.actors.SentMessage;
import com.mentalresonance.dust.core.msgs.DeadLetter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs a server to receive remote messages. Spawns a new virtual thread to handle a new connection but
 * each thread leaves the connection open.
 */
@Slf4j
public class TCPObjectServer {
    final int port;
    final CompletableFuture<Boolean> haveStopped;
    Thread serverThread;
    private final ActorSystem actorSystem;

    LinkedBlockingQueue<TCPObjectSocket> workerSockets;

    Cache<Long, WorkerBee> workerBees;

    /**
     * A server
     * @param port on port number
     * @param actorSystem
     * @param haveStopped completed when stopped
     */
    public TCPObjectServer(
        int port,
        ActorSystem actorSystem,
        CompletableFuture<Boolean> haveStopped,
        int maxIncomingConnections,
        List<Class<?>> registeredClasses) {
        this.port = port;
        this.haveStopped = haveStopped;
        this.actorSystem = actorSystem;

        workerSockets = new LinkedBlockingQueue<>(maxIncomingConnections+1);
        workerBees = Caffeine
            .newBuilder()
            .maximumSize(maxIncomingConnections)
            .evictionListener((Long key, WorkerBee wb, RemovalCause cause) -> {
                if (wb != null) {
                    wb.getThread().interrupt();
                }
            })
            .build();
        for (int i = 0; i < maxIncomingConnections+1; ++i) {
            workerSockets.add(new TCPObjectSocket(registeredClasses));
        }
    }

    /**
     * Start server - accept an incoming connection and wrap its channel with a TCPObjectSocket
     * Get the ID of that ObjectSocket - which identifies the unique pair of ActorRefs at either end
     * of the conversation. Dispatch it off to the appropriate worker bee.
     * @throws IOException on errors
     */
    public void start(ActorSystem actorSystem) throws IOException {

        WorkerBee DUMMY_WB = new WorkerBee();

        serverThread = Thread.ofVirtual().start(
            () ->  {
                ServerSocketChannel serverSocketChannel = null;
                try {
                    serverSocketChannel = ServerSocketChannel.open();
                    serverSocketChannel.bind(new InetSocketAddress(port));
                    log.info ("Remoting Server started on socket {}", serverSocketChannel.socket());

                    while (true)
                    {
                        log.trace("{} Waiting for connection", this);
                        SocketChannel client = serverSocketChannel.accept();   // blocking accept
                        log.trace("{} Accepted connection from {}", this, client.getRemoteAddress());
                        TCPObjectSocket socket = workerSockets.poll(5, TimeUnit.SECONDS);
                        if (socket == null) {
                            log.warn("Server saturated: No available worker sockets");
                            client.close();
                            return;
                        }

                        client.configureBlocking(true);
                        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
                        socket.wrap(client);

                        int payloadSize = socket.readHeader();

                        long id = socket.getCombinedIds();

                        WorkerBee wb = workerBees.get(id, k -> {
                            // log.info("---------- new bee for {} src:{} target:{}", id, socket.getSrcId(), socket.getTargetId());
                            WorkerBee workerBee;
                            try {
                                workerBee = new WorkerBee(k, socket, payloadSize);
                                Thread wbThread = Thread.startVirtualThread(workerBee);
                                workerBee.setThread(wbThread);
                                return workerBee;
                            } catch (Exception e) {
                                log.error("Error creating worker bee for {}: {}", id, e.getMessage());
                                returnSocket(socket);
                                return DUMMY_WB;
                            }

                        });
                        if (wb == DUMMY_WB) {
                            workerBees.invalidate(id);
                        }
                    }
                }
                catch (ClosedByInterruptException ignored) {  // How we stop
                    // All worker sockets will be closed
                    if (serverSocketChannel != null && !serverSocketChannel.socket().isClosed()) {
                        try {
                            serverSocketChannel.socket().close();
                        } catch (IOException e) {
                            log.error ("Error closing server socket: {} on port: {}", e.getMessage(), port);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (serverSocketChannel != null && !serverSocketChannel.socket().isClosed()) {
                        try {
                            serverSocketChannel.socket().close();
                        } catch (IOException ioe) {
                            log.error ("Error closing server socket: {} on port: {}", ioe.getMessage(), port);
                        }
                    }
                }

                workerBees.asMap().values().forEach(w -> {
                    if (w != null) {
                        w.getThread().interrupt();
                    }
                });
                workerSockets.clear();

                log.info("Remoting Server stopped on socket: {}", serverSocketChannel.socket());

                haveStopped.complete(true);
            }
        );
    }

    public void returnSocket(TCPObjectSocket socket) {
        socket.close();
        socket.restoreInitialCapacity();
        workerSockets.offer(socket);
    }

    /**
     * Stops the server. The assumption here is the ActorSystem (and hence the application) is shutting down
     * The server will close this connection so we don't.
     */
    public void stop() {
        try {
            log.info("Stopping server on port " + port);
            serverThread.interrupt();
        }
        catch (Exception e) {
            log.error("Stopping server: %s".formatted(e.getMessage()));
        }
    }

    private record Digest(int payloadSize, TCPObjectSocket socket) {}

    /*
     * Sit on a connection reading messages and handing them off. The connection is *the* channel for
     * messages between a fixed pair of Actors. The worker bee is selected by src and target Ids so
     * so all messages between a fixed pair of (remote) Actors get serialized through this thread
     * thereby preserving the Dust message ordering requirement.
     */
    private class WorkerBee implements Runnable {

        @Getter @Setter Thread thread;

        TCPObjectSocket socket;
        long id;

        WorkerBee() { } // For dummy

        WorkerBee(long id, TCPObjectSocket socket, int payloadSize) throws Exception {
            this.id = id;
            this.socket = socket;
            try {
                actorSystem.connectionAccepted((SentMessage) socket.receivePayload(payloadSize));
            } catch (Exception e) {
                log.error("WorkerBee error in construction {}: {}", id, e.getMessage());
                throw e;
            }
        }

        public void run()
        {
            int payloadSize;

            while(!actorSystem.isStopped())
            {
                try {
                    boolean isNullSender = socket.isNullSender();
                    payloadSize = socket.readHeader();

                    if (0 == payloadSize) { // 'null' sent which means the other end is going away so will we
                        break;
                    }
                    else {
                        actorSystem.connectionAccepted((SentMessage) socket.receivePayload(payloadSize));
                    }
                }
                // Naturally stopping - because of local eviction or client close socket
                catch (InterruptedException | IOException ie) {
                    break;
                }
                catch (Exception e) {
                    log.error("WorkerBee error in run {}: {}", id, e.getMessage());
                    break;
                }
            }
            if (actorSystem.isStopped())
                log.trace("Stopped because of system shutdown");

            returnSocket(socket); // This will close the underlying socket
            workerBees.invalidate(id); // Will not trigger eviction listener
        }
    }

}
