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

import com.mentalresonance.dust.core.services.SerializationService;
import com.mentalresonance.dust.core.system.ActorSystemConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.nustaq.net.TCPObjectSocket;
import org.nustaq.serialization.FSTConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

/**
 * Runs a server to receive remote messages. Spawns a new virtual thread to handle a new connection but
 * each thread leaves the connection open.
 *
 * Based on TCPObjectServer by reudi
 */
@Slf4j
public class CoreTCPObjectServer {

    ServerSocket welcomeSocket;
    final FSTConfiguration conf;
    final int port;
    volatile boolean terminated;
    final ActorSystemConnectionManager actorSystemConnectionManager;
    final CompletableFuture<Boolean> haveStopped;

    /**
     * A server
     * @param conf Serialization config
     * @param port on port number
     * @param actorSystemConnectionManager manage incoming connections
     * @param haveStopped completed when stopped
     */
    public CoreTCPObjectServer(
            FSTConfiguration conf,
            int port,
            ActorSystemConnectionManager actorSystemConnectionManager,
            CompletableFuture<Boolean> haveStopped) {
        this.conf = conf;
        this.port = port;
        this.actorSystemConnectionManager = actorSystemConnectionManager;
        this.haveStopped = haveStopped;
    }

    /**
     * Start server with given listener
     * @param listener the listener
     * @throws IOException on errors
     */
    public void start(final org.nustaq.net.TCPObjectServer.NewClientListener listener) throws IOException {
        final ActorSystemConnectionManager connectionManager = actorSystemConnectionManager;
        new Thread("server "+port) {
            public void run() {
                try {
                    welcomeSocket = new ServerSocket(port);

                    while (!terminated)
                    {
                        final Socket connectionSocket = welcomeSocket.accept();
                        connectionManager.addRemoteSocket(connectionSocket);
                        Thread.startVirtualThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    listener.connectionAccepted(new TCPObjectSocket(connectionSocket,conf));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    try {
                        welcomeSocket.close();
                    } catch (IOException e) {
                        log.error("Could not close welcomeSocket " + e.getMessage());
                    } finally {
                        haveStopped.complete(true);
                    }
                }
            }
        }.start();
    }

    /**
     * Stops the server. The assumption here is the ActorSystem (and hence the application) is shutting down
     * The server will close this connection so we don't.
     */
    public void stop() {
        try {
            terminated = true;
            TCPObjectSocket socket = new TCPObjectSocket("localhost", port, SerializationService.getFstConfiguration());
            socket.writeObject(null);
            socket.flush();
        }
        catch (Exception e) {
            log.error("Stopping server: %s".formatted(e.getMessage()));
        }
    }
}
