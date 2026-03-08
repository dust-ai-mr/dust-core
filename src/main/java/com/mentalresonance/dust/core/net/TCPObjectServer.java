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

import com.mentalresonance.dust.core.actors.ActorSystem;
import com.mentalresonance.dust.core.actors.SentMessage;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

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
    final int CONNECTIONS = 16;
    LinkedBlockingQueue<TCPObjectSocket> workers = new LinkedBlockingQueue<>(CONNECTIONS);
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
            workers.add(new TCPObjectSocket());
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
                try {
                    ServerSocketChannel server = ServerSocketChannel.open();
                    server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                    server.bind(new InetSocketAddress(port));
                    while (!terminated)
                    {
                        SocketChannel client = server.accept();   // blocking accept
                        Thread.startVirtualThread(() -> connectionServer(actorSystem, client));
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                log.info("Server stopped");
            }
        };

        serverThread.start();
    }

    /**
     * Sit on this connection and process messages until we get a null message.
     * @param actorSystem
     * @param client
     */
    protected void connectionServer( ActorSystem actorSystem, SocketChannel client) {
        boolean running = true;
        try {
            TCPObjectSocket socket = workers.take();
            socket.init();
            socket.wrap(client);
            while (running) {
                try {
                    SentMessage msg = (SentMessage) socket.receive();
                    if (null == msg) {
                        running = false;
                        returnSocket(socket);
                    }
                    else {
                        actorSystem.connectionAccepted(msg, this);
                        // Ack
                        socket.init();
                        socket.send(null);
                        socket.init();
                    }
                } catch (Exception e) {
                    log.error("Error handling client: %s".formatted(e.getMessage()));
                    running = false;
                }
            }
        } catch (Exception e) {
            log.error("Connection server: %s".formatted(e.getMessage()));
        }

    }

    public void returnSocket(TCPObjectSocket socket) {
        try {
            socket.close();
            workers.put(socket);
        }
        catch (Exception e) {
            log.error("Error returning socket to pool: %s".formatted(e.getMessage()));
        }
    }


    /**
     * Stops the server. The assumption here is the ActorSystem (and hence the application) is shutting down
     * The server will close this connection so we don't.
     */
    public void stop() {
        try {
            log.info("Stopping server on port " + port);
            terminated = true;
            haveStopped.complete(true);
        }
        catch (Exception e) {
            log.error("Stopping server: %s".formatted(e.getMessage()));
        }
    }
}
