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

package com.mentalresonance.dust.core.system;

import com.mentalresonance.dust.core.services.SerializationService;
import lombok.extern.slf4j.Slf4j;
import org.nustaq.net.TCPObjectSocket;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Maintain a pool of connections from My Actor system -> Remote Actor system.
 * Dynamically create (in fixed pool size of SocketsPerRemote), otherwise performance would
 * be terrible if we had to open/close for each tell()
 */
@Slf4j
public class ActorSystemConnectionManager {

    private static final Object SocketLock = new Object();
    private static final Integer SocketsPerRemote = 16;

    final long PING_INTERVAL = 15000; // How often we check
    final long PING_TIMEOUT = 30000;  // If haven't heard from a connection over this time then flush it

    final Thread managerThread;

    /**
     * When the object server receives a new connection we put its socket here. This means
     * if stopping we can close our ends.
     */
    private final ConcurrentHashMap<Socket, Boolean> remoteSockets = new ConcurrentHashMap<>();

    /**
     * A remote System may go down but our sockets don't know about. So for now we simply check
     * every so often to see if we have heard from the System - if not we remove the connections and wait
     * to hear again.
     */
    public ActorSystemConnectionManager() {
        managerThread = Thread.startVirtualThread(new Runnable() {
            @Override
            public void run() {
                boolean running = true;
                while (running) {
                    try {
                        Thread.sleep(PING_INTERVAL);
                        flushPool(false);
                    }
                    catch (InterruptedException e) {
                        running = false;
                    }
                }
                log.info("Pool manager shutdown");
            }
        });
    }

    /**
     * Keep a list of prepared connections to host:port for remote Actor (systems)
     * Hash(remote-host, remote-port, remote system) -> pool of connections to the remote system
     * <p>
     * Since we wish to return sockets to these pools we want to ensure that querying the socket for the key (based
     * on host and port) is completely reliable/reversible. Sadly, at least as I can tell this is not the case since
     * we tend to get things like localhost - 127.0.0.1 conflicts. So we wrap the socket in a class which contains
     * the key it was stored under, so when we return it we have no doubts.
     */
    private final ConcurrentHashMap<String, ConnectionPool> remoteActorSystems = new ConcurrentHashMap<>();

    /**
     * Get key from path
     *
     * @param path
     * @return
     */
    private String remoteKey(URI path) {
        return "%s:%d".formatted(path.getHost(), path.getPort());
    }

    /**
     * Flush the socket pool
     * @param all if true the completely drain the pool, otherwise only those who haven't been pinged recently
     */
    public void flushPool(boolean all) {
        synchronized (SocketLock) {
            try {
                for (String key : remoteActorSystems.keySet()) {
                    ConnectionPool pool = remoteActorSystems.get(key);
                    if (all || System.currentTimeMillis() - pool.lastPing > PING_TIMEOUT) {
                        log.trace("Flushing pool for key: %s".formatted(key));
                        for (WrappedTCPObjectSocket socket : pool.q) {
                            if (! socket.tcpObjectSocket.isClosed()) {
                                socket.tcpObjectSocket.writeObject(null);
                                socket.tcpObjectSocket.flush();
                                socket.tcpObjectSocket.getSocket().close();
                            } else
                                log.warn("Socket to remote actor system {} was closed", key);
                        }
                        remoteActorSystems.remove(key);
                    }
                }
            } catch (Exception e) {
                log.error("flushPool(): %s".formatted(e.getMessage()));
            }
        }
    }

    /**
     * Get a wrapped socket to the given uri
     * @param uri target uri
     * @return The wrapped socket object
     * @throws IOException on errors
     * @throws InterruptedException if interrupted
     */
    public WrappedTCPObjectSocket getSocket(URI uri) throws IOException, InterruptedException {
        String key = remoteKey(uri);

        synchronized (SocketLock) {
            if (!remoteActorSystems.containsKey(key)) {
                ConnectionPool pool = new ConnectionPool();
                log.trace("Creating new pool for: %s".formatted(key));
                pool.q = new LinkedBlockingQueue<>();

                for (int i = 0; i < SocketsPerRemote; ++i) {
                    pool.q.add(
                            new WrappedTCPObjectSocket(
                                    key,
                                    new TCPObjectSocket(uri.getHost(), uri.getPort(), SerializationService.getFstConfiguration())
                            )
                    );
                }
                pool.lastPing = System.currentTimeMillis();
                remoteActorSystems.put(key, pool);
            }
        }
        return remoteActorSystems.get(key).q.take();
    }

    /**
     * Add incoming connection to managed list
     * @param socket of incoming connection
     */
    public void addRemoteSocket(Socket socket) {
        remoteSockets.put(socket, true);
    }

    /**
     * Close incoming connection. We send a null message so the client knows to close his end.
     * @param socket - socket to close
     */
    public void closeRemoteSocket(Socket socket)  {
        remoteSockets.remove(socket);
        /*
         * If the 'remote' socket was actually the server shutdown null message sender then it has already been
         * closed by the handler.
         */
        try {
            TCPObjectSocket remoteSocket = new TCPObjectSocket(socket, SerializationService.getFstConfiguration());
            remoteSocket.writeObject(null);
            remoteSocket.flush();
            remoteSocket.close();
        }
        catch(Exception ignored) {}
    }

    private void closeRemoteSockets() throws Exception {
        for(Socket socket: remoteSockets.keySet()) {
            closeRemoteSocket(socket);
        }
    }

    /**
     * Shutdown the manager. We close outgoing and incoming connections by sending
     * null messages on them and then closing the sockets.
     *
     * @throws Exception on error
     */
    public void shutdown() throws Exception {
        managerThread.interrupt();
        flushPool(true);
        closeRemoteSockets();
        log.info("Shutdown");
    }

    /**
     * Return wrapped socket to the pool
     * @param objectSocket the socket
     */
    public void returnSocket(WrappedTCPObjectSocket objectSocket) {
        // log.trace("Returning socket");
        remoteActorSystems.get(objectSocket.key).q.add(objectSocket);
    }

    /**
     * Takes a TCPObject and associates it with a key
     */
    public static class WrappedTCPObjectSocket {
        final String key;
        /**
         * The TCPObjectSocket
         */
        public final TCPObjectSocket tcpObjectSocket;

        /**
         * Constructor
         * @param key to associate with the ..
         * @param tcpObjectSocket .. socket
         */
        public WrappedTCPObjectSocket(String key, TCPObjectSocket tcpObjectSocket) {
            this.key = key;
            this.tcpObjectSocket = tcpObjectSocket;
        }

    }

    private static class ConnectionPool {
        long lastPing;
        LinkedBlockingQueue<WrappedTCPObjectSocket> q;
    }
}
