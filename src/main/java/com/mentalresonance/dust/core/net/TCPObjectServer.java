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
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.ActorSystem;
import com.mentalresonance.dust.core.actors.SentMessage;
import lombok.extern.slf4j.Slf4j;
import org.jctools.queues.MpscLinkedQueue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Runs a server to receive remote messages. Spawns a new virtual thread to handle a new connection but
 * each thread leaves the connection open.
 */
@Slf4j
public class TCPObjectServer {
    final int port;
    volatile boolean terminated;
    final ActorSystemConnectionManager actorSystemConnectionManager;
    final CompletableFuture<Boolean> haveStopped;
    Thread serverThread;
    private ActorSystem actorSystem;
    final int CONNECTIONS = 32;
    LinkedBlockingQueue<TCPObjectSocket> workerSockets = new LinkedBlockingQueue<>(CONNECTIONS);

    Cache<Integer, WorkerBee> workerBees = Caffeine
        .newBuilder()
        .maximumSize(CONNECTIONS)
        .removalListener((Integer key, WorkerBee wb, RemovalCause cause) -> {
            if (wb != null) {
                log.warn("WorkerBee {} removed from cache on port {} {}", key, actorSystem.getPort(), cause);
                wb.thread().interrupt();
            }
        })
        .build();

    /**
     * A server
     * @param port on port number
     * @param actorSystemConnectionManager manage incoming connections
     * @param haveStopped completed when stopped
     */
    public TCPObjectServer(
            int port,
            ActorSystemConnectionManager actorSystemConnectionManager,
            CompletableFuture<Boolean> haveStopped) {
        this.port = port;
        this.actorSystemConnectionManager = actorSystemConnectionManager;
        this.haveStopped = haveStopped;
        this.actorSystem = actorSystemConnectionManager.getActorSystem();

        for (int i = 0; i < CONNECTIONS; ++i) {
            workerSockets.add(new TCPObjectSocket());
        }
    }

    /**
     * Start server
     * @throws IOException on errors
     */
    public void start(ActorSystem actorSystem) throws IOException {
        serverThread = new Thread("server "+port)
        {
            public void run() {
                ServerSocketChannel server = null;
                try {
                    server = ServerSocketChannel.open();
                    server.bind(new InetSocketAddress(port));
                    log.info ("Remoting Server started on socket {}", server.socket());
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

                        int senderId = socket.getActorRefId();
                        if (senderId != ActorRef.NullActorRefID) {
                            WorkerBee wb = workerBees.get(senderId, k -> {
                                WorkerBee workerBee = new WorkerBee(k);
                                Thread.startVirtualThread(workerBee);
                                return workerBee;
                            });
                            wb.processTCBObject(socket);
                            LockSupport.unpark(wb.thread());
                        }
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
                log.info("Remoting Server stopped on socket: {}", server.socket());
                haveStopped.complete(true);
            }
        };

        serverThread.start();
    }

    /**
     * Sit on this connection and process messages until we get a null message.
     * @param actorSystem
     * @param client
     */
    protected void connectionServer(ActorSystem actorSystem, SocketChannel client) {
        TCPObjectSocket socket = null;

        try {
            // Poll instead of take to avoid indefinite blocking if the pool is exhausted
            socket = workerSockets.poll(5, TimeUnit.SECONDS);
            if (socket == null) {
                log.warn("Server saturated: No available worker sockets");
                client.close();
                return;
            }

            client.configureBlocking(true);
            client.setOption(StandardSocketOptions.TCP_NODELAY, true);
            client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            socket.wrap(client);

            while (!terminated)
            {
                SentMessage sentMsg = (SentMessage)socket.receive();

                if (sentMsg == null) break; // Client sent termination signal (length 0)

                ActorRef sender = sentMsg.sender();
                if (sender != null) {
                    WorkerBee wb = workerBees.get(sender.hashCode(), k -> {
                        WorkerBee workerBee = new WorkerBee(k);
                        Thread workerBeeThread = Thread.startVirtualThread(workerBee);
                        return workerBee;
                    });
                    wb.queue.offer(sentMsg);
                    LockSupport.unpark(wb.thread());
                }
                else {  // Have to serialize the old way ... :(
                    actorSystem.connectionAccepted(sentMsg);
                    // Ack
                    socket.send(null);
                }
            }
        }
        catch (InterruptedException e) {
            log.error("Interrupted while handling client {}", client);
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            log.error("Error handling client {}: {}", client, e.getMessage());
        }
        finally {
            log.trace("Closing connection to {}", client);
            if (socket != null) {
                returnSocket(socket);
            }
        }
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

    private class WorkerBee implements Runnable {
        public final MpscLinkedQueue<SentMessage> queue = new MpscLinkedQueue<>();
        int id;

        WorkerBee(int id) {
            this.id = id;
        }

        public void run() {

            while(true)
            {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (0 == queue.drain((sentMessage) -> { actorSystem.connectionAccepted(sentMessage); })) {
                    LockSupport.park();
                }
            }
            workerBees.invalidate(id);
        }

        public void processTCBObject(TCPObjectSocket socket) throws Exception {
            SentMessage sentMessage =  (SentMessage)socket.receive();
            queue.offer(sentMessage);
        }

        Thread thread() {
            return Thread.currentThread();
        };
    }

}
