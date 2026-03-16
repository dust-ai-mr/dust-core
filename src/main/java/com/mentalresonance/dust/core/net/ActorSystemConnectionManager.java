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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Maintain a pool of connections from My Actor system -> Remote Actor system.
 * Dynamically create (in fixed pool size of SocketsPerRemote), otherwise performance would
 * be terrible if we had to open/close for each tell().
 */
@Slf4j
public class ActorSystemConnectionManager {

    private static final Object SocketLock = new Object();
    private static final Integer SocketsPerRemote = 8;

    final long PING_INTERVAL = 10000; // How often we check
    final long PING_TIMEOUT = 30000;  // If haven't heard from a connection over this time then flush it

    final Thread managerThread;

    @Getter
    ActorSystem actorSystem;

    /**
     * A remote System may go down but our sockets don't know about. So for now we simply check
     * every so often to see if we have heard from the System - if not we remove the connections and wait
     * to hear again.
     */
    public ActorSystemConnectionManager(ActorSystem actorSystem) {

        this.actorSystem = actorSystem;

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
     * Since we wish to return sockets to these pools we want to ensure that querying the socket for the key (based
     * on host and port) is completely reliable/reversible. Sadly, at least as I can tell this is not the case since
     * we tend to get things like localhost - 127.0.0.1 conflicts. So we wrap the socket in a class which contains
     * the key it was stored under, so when we return it we have no doubts.
     */
    final ConcurrentHashMap<String, ConnectionPool> remoteActorSystems = new ConcurrentHashMap<>();

    /**
     * Get key from path
     *
     * @param path
     * @return
     */
    private String remoteKey(URI path) {
        return path.getHost() + ":" +  path.getPort();
    }

    /**
     * Flush the socket pool
     * @param force if true the completely drain the pool, otherwise only those who haven't been pinged recently
     */
    public void flushPool(boolean force) {
        synchronized (SocketLock) {
            for (String key : remoteActorSystems.keySet()) {
                try {
                    ConnectionPool pool = remoteActorSystems.get(key);
                    pool.flush(force);
                } catch (Exception e) {
                    log.error("flushPool(): %s".formatted(e.getMessage()));
                }
            }
        }
    }

    public void flushPool(URI uri) {
        synchronized (SocketLock) {
            try {
                ConnectionPool pool = remoteActorSystems.get(remoteKey(uri));
                pool.flush(true);
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
        ConnectionPool pool;
        int retries = 5;

        while (--retries >= 0) {
            try {
                if (!remoteActorSystems.containsKey(key)) {
                    pool = new ConnectionPool(SocketsPerRemote, key, uri.getHost(), uri.getPort());
                    remoteActorSystems.put(key, pool);
                }
                return remoteActorSystems.get(key).acquire(uri.getPath());
            }
            catch (IOException e) {
                log.warn("Failed to get connection to: {}. Retrying.", uri);
                pool = remoteActorSystems.remove(key);
                if (pool != null)
                    try {
                        pool.flush(true);
                    } catch (Exception ignored) {}
                Thread.sleep(3000L);
            }
        }
        log.error("Cannot get connection to remote actor system: {}", uri);
        throw new IOException();
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        managerThread.interrupt();
        flushPool(true);
        log.info("Shutdown");
    }

    /**
     * Return wrapped socket to the pool
     * @param objectSocket the socket
     */
    public void returnSocket(WrappedTCPObjectSocket objectSocket) {
        // log.trace("Returning socket");
        ConnectionPool pool = remoteActorSystems.get(objectSocket.key);
        if (pool != null) {
            objectSocket.tcpObjectSocket.restoreInitialCapacity();
            pool.restore(objectSocket);
        } else
            log.trace("Returning socket to unknown pool: {}", objectSocket.key);
    }

    /**
     * Takes a TCPObject and associates it with a key
     */
    public static class WrappedTCPObjectSocket {
        final String key;
        public String path; // To Actor when the Socket is used
        /**
         * The TCPObjectSocket
         */
        public final TCPObjectSocket tcpObjectSocket;

        /**
         * Constructor
         * @param key to associate with the socket
         * @param tcpObjectSocket .. socket
         */
        public WrappedTCPObjectSocket(String key, TCPObjectSocket tcpObjectSocket) {
            this.key = key;
            this.tcpObjectSocket = tcpObjectSocket;

        }

    }

    /**
     * Pool of connections for one remote ActorSystem.
     */
    private class ConnectionPool {
        String key, host;
        long lastAccess;
        int port;
        LinkedBlockingDeque<WrappedTCPObjectSocket> connections;

        ConnectionPool(int size, String key, String host, int port) throws IOException {
            this.key = key;
            this.host = host;
            this.port = port;

            connections = new LinkedBlockingDeque<>();

            for (int i = 0; i < SocketsPerRemote; ++i) {
                connections.add(new WrappedTCPObjectSocket(key, new TCPObjectSocket()));
            }
            lastAccess = System.currentTimeMillis();
        }


        WrappedTCPObjectSocket acquire(String path) throws IOException, InterruptedException {
            WrappedTCPObjectSocket socket =  connections.take();
            if (socket.tcpObjectSocket.isClosed()) {
                socket.tcpObjectSocket.wrap(SocketChannel.open(new InetSocketAddress(host, port)));
            }
            socket.tcpObjectSocket.init();
            socket.path = path;
            lastAccess = System.currentTimeMillis();
            return socket;
        }

        /*
            Put the connection at the front of the Q so hopefully it will be reused over those
            WrappedTCPObjectSocket which have still to open a connection (and reduce load on the server)
         */
        void restore(WrappedTCPObjectSocket objectSocket) {
            connections.add(objectSocket); // Return socket
        }

        /*
            Pool management. We keep a track (lastAccess) of when a connection was acquired i.e.
            there was activity in the pool. If too much time passes (PING_TIMEOUT) we drop the pool after
            signaling the remote server to stop the server for this pool.
         */

        /**
         * Flush connection pool of all sockets associated with remote ActorSystem of uri
         * @param uri
         */
        public void flushPool(URI uri)
        {
            ConnectionPool pool = remoteActorSystems.get(remoteKey(uri));

            if (pool != null) {
                pool.flush(true);
            }
            else
                log.warn("Flushing unknown pool for: {}", uri);
        }

        /**
         * Flushes the connection pool by closing and removing all associated sockets.
         * If the `force` parameter is true or the timeout since the last access has been exceeded,
         * the flush operation is performed. Closes remote server associated with this pool.
         *
         * @param force A boolean flag indicating whether the flush should be forced. If true,
         *              the connection pool is flushed irrespective of the timeout condition.
         */
        public void flush(boolean force) {
            if (force || System.currentTimeMillis() - lastAccess > PING_TIMEOUT)
            {
                log.trace("[{}] Flushing pool for key: {}", actorSystem.getPort(), key);

                boolean closedRemote = false;
                for (ActorSystemConnectionManager.WrappedTCPObjectSocket socket : connections)
                {
                    try {
                        if (!socket.tcpObjectSocket.isClosed()) {
                            if (! closedRemote) {
                                socket.tcpObjectSocket.send(null);
                                closedRemote = true;
                            }
                            socket.tcpObjectSocket.close();
                        }
                        connections.remove(socket);
                    }
                    catch(Exception e) {
                        log.error("Flushing pool: %s".formatted(e.getMessage()));
                    }
                }
                remoteActorSystems.remove(key);
            }
        }
    }
}
