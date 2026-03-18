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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
    final int CONNECTIONS = 64;

    LinkedBlockingQueue<TCPObjectSocket> workerSockets = new LinkedBlockingQueue<>(CONNECTIONS+1);

    Cache<Long, WorkerBee> workerBees = Caffeine
        .newBuilder()
        .maximumSize(CONNECTIONS)
        .evictionListener((Long key, WorkerBee wb, RemovalCause cause) -> {
            if (wb != null) {
                wb.getThread().interrupt();
            }
        })
        .build();

    /**
     * A server
     * @param port on port number
     * @param actorSystem
     * @param haveStopped completed when stopped
     */
    public TCPObjectServer(
            int port,
            ActorSystem actorSystem,
            CompletableFuture<Boolean> haveStopped) {
        this.port = port;
        this.haveStopped = haveStopped;
        this.actorSystem = actorSystem;

        for (int i = 0; i < CONNECTIONS+1; ++i) {
            workerSockets.add(new TCPObjectSocket());
        }
    }

    /**
     * Start server - accept an incoming connection and wrap its channel with a TCPObjectSocket
     * Get the ID of that ObjectSocket - which identifies the unique pair of ActorRefs at either end
     * of the conversation. Dispatch it of to the appropriate worker bee.
     * @throws IOException on errors
     */
    public void start(ActorSystem actorSystem) throws IOException {
        serverThread = Thread.ofVirtual().start(
            () ->  {
                ServerSocketChannel server = null;
                try {
                    server = ServerSocketChannel.open();
                    server.bind(new InetSocketAddress(port));
                    log.trace ("Remoting Server started on socket {}", server.socket());
                    while (true)
                    {
                        SocketChannel client = server.accept();   // blocking accept
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
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            Thread wbThread = Thread.startVirtualThread(workerBee);
                            workerBee.setThread(wbThread);
                            return workerBee;
                        });
                    }
                }
                catch (ClosedByInterruptException ignored) {  // How we stop
                    // All worker sockets will be closed
                    if (server != null && !server.socket().isClosed()) {
                        try {
                            server.socket().close();
                        } catch (IOException e) {
                            log.error ("Error closing server socket: {} on port: {}", e.getMessage(), port);
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                log.trace("Remoting Server stopped on socket: {}", server.socket());
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
     * messages between a fixed pair of Actors.
     */
    private class WorkerBee implements Runnable {

        @Getter @Setter Thread thread;

        TCPObjectSocket socket;
        long id;

        WorkerBee(long id, TCPObjectSocket socket, int payloadSize) throws Exception {
            this.id = id;
            this.socket = socket;
            actorSystem.connectionAccepted((SentMessage) socket.receivePayload(payloadSize));
            if (socket.isNullSender())
                socket.send(null);

        }

        public void run()
        {
            int payloadSize;

            while(true)
            {
                try {
                    payloadSize = socket.readHeader();
                    if (0 == payloadSize) { // 'null' sent which means the other end is going away ...
                        if (socket.isNullSender()) {
                            socket.send(null);
                            break;
                        }
                    }
                    else {
                        actorSystem.connectionAccepted((SentMessage) socket.receivePayload(payloadSize));
                        if (socket.isNullSender())
                            socket.send(null);
                    }
                }
                // Naturally stopping - because of local eviction or client close socket
                catch (InterruptedException | IOException ie) {
                    break;
                }
                catch (Exception e) {
                    log.error("WorkerBee {}: {}", id, e.getMessage());
                    break;
                }
            }
            returnSocket(socket); // This will close the underlying socket
            workerBees.invalidate(id); // Will not trigger eviction listener
        }
    }

}
