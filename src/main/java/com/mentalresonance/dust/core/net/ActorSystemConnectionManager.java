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
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Maintain a pool of connections from My Actor system -> Remote Actor system.
 * Dynamically create (in fixed pool size of SocketsPerRemote), otherwise performance would
 * be terrible if we had to open/close for each tell()
 */
@Slf4j
public class ActorSystemConnectionManager {

    private static final Object SocketLock = new Object();
    private static final Integer SocketsPerRemote = 4;

    final long PING_INTERVAL = 15000; // How often we check
    final long PING_TIMEOUT = 30000;  // If haven't heard from a connection over this time then flush it

    final Thread managerThread;

    @Getter
    ActorSystem actorSystem;
    /**
     * When the object server receives a new connection we put its socket here. This means
     * if stopping we can close our ends.
     */
    private final ConcurrentHashMap<SocketChannel, Boolean> remoteSockets = new ConcurrentHashMap<>();

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
        return "%s:%d".formatted(path.getHost(), path.getPort());
    }

    /**
     * Flush the socket pool
     * @param all if true the completely drain the pool, otherwise only those who haven't been pinged recently
     */
    public void flushPool(boolean all) {
        synchronized (SocketLock) {
            for (String key : remoteActorSystems.keySet()) {
                try {
                    ConnectionPool pool = remoteActorSystems.get(key);
                    pool.flush(all);
                } catch (Exception e) {
                    log.error("flushPool(): %s".formatted(e.getMessage()));
                }
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
                synchronized (SocketLock) {
                    if (!remoteActorSystems.containsKey(key)) {
                        pool = new ConnectionPool(SocketsPerRemote, key, uri.getHost(), uri.getPort());
                        remoteActorSystems.put(key, pool);
                    }
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
     * Add incoming connection to managed list
     * @param socket of incoming connection
     */
    public void addRemoteSocket(SocketChannel socket) {
        remoteSockets.put(socket, true);
    }

    /**
     * Close incoming connection. We send a null message so the client knows to close his end.
     * @param remoteSocket - socket to close
     */
    public void closeRemoteSocket(SocketChannel remoteSocket)  {
        remoteSockets.remove(remoteSocket);
        /*
         * If the 'remote' socket was actually the server shutdown null message sender then it has already been
         * closed by the handler.
         */
        try {
            remoteSocket.write((ByteBuffer)null);
            remoteSocket.close();
        }
        catch(Exception ignored) {}
    }

    private void closeRemoteSockets() throws Exception {
        for(SocketChannel socket: remoteSockets.keySet()) {
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
        ConnectionPool pool = remoteActorSystems.get(objectSocket.key);
        if (pool != null) {
            objectSocket.tcpObjectSocket.init();
            pool.restore(objectSocket);
        } else
            log.warn("Returning socket to unknown pool: {}", objectSocket.key);
    }

    /**
     * Flush connection pool of all sockets associate with remote ActorSystem of uri
     * @param uri
     * @throws Exception
     */
    public void flushPool(URI uri) throws Exception {
        // log.trace("Returning socket");
        ConnectionPool pool = remoteActorSystems.get(remoteKey(uri));
        if (pool != null) {
            pool.flush(true);
        } else
            log.warn("Flushing unknown pool for: {}", uri);
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
        String key;
        long lastPing;
        LinkedBlockingDeque<WrappedTCPObjectSocket> q;

        ConnectionPool(int size, String key, String host, int port) throws IOException {
            this.key = key;
            q = new LinkedBlockingDeque<>();

            for (int i = 0; i < SocketsPerRemote; ++i) {
                SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
                q.add(
                    new WrappedTCPObjectSocket(key, new TCPObjectSocket(channel))
                );
            }
            lastPing = System.currentTimeMillis();
        }


        WrappedTCPObjectSocket acquire(String path) throws IOException, InterruptedException {
            WrappedTCPObjectSocket socket =  q.take();
            socket.tcpObjectSocket.init();
            socket.path = path;
            return socket;
        }

        /*
            Put the connection at the front of the Q so hopefully it will be reused over those
            WrappedTCPObjectSocket which have still to open a connection (and reduce load on the server)
         */
        void restore(WrappedTCPObjectSocket objectSocket) {
            q.addFirst(objectSocket); // Return socket
        }

        public void flush(boolean all) throws Exception {
            if (all || System.currentTimeMillis() - lastPing > PING_TIMEOUT) {
                log.trace("Flushing pool for key: %s".formatted(key));
                for (ActorSystemConnectionManager.WrappedTCPObjectSocket socket : q) {
                    try {
                        if (! socket.tcpObjectSocket.isClosed()) {
                            socket.tcpObjectSocket.send(null);
                            socket.tcpObjectSocket.close();
                        } else {
                            log.warn("Socket to remote actor system {} was closed", key);
                            q.remove(socket);
                        }
                    } catch(Exception ignored) {}
                }
                remoteActorSystems.remove(key);
            }
        }
    }
}
