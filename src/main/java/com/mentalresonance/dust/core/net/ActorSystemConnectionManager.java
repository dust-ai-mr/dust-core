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
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main connections between pairs of ActorRefs -- each has an int ID (hash of path) and we
 * combine them intop a Long
 */
@Slf4j
public class ActorSystemConnectionManager {

    LinkedBlockingQueue<TCPObjectSocket> freeSockets = new LinkedBlockingQueue<>();;
    Cache<Long, TCPObjectSocket> connections;

    public ActorSystemConnectionManager() {
        this(64);
    }
    /**
     * Prepare TCPObject sockets since building serializers is expensive
     */
    public ActorSystemConnectionManager(int size) {

        connections = Caffeine
            .newBuilder()
            .maximumSize(size)
            .removalListener((Long key, TCPObjectSocket socket, RemovalCause cause) -> {
                log.trace("Removing socket: {} for cause: {}", socket, cause);
                try {
                    socket.send(null);
                    socket.close();
                } catch (IOException ie) {
                    // Pipe may have been broken
                    socket.close();
                }
                catch (Exception e) {
                    log.error("Error removing socket: {}", e.getMessage());
                }

                freeSockets.offer(socket);
            })
            .build();

        for (int i = 0; i < size; ++i) {
            freeSockets.add(new TCPObjectSocket());
        }
    }

    public TCPObjectSocket getSocket(ActorRef sender, int srcId, int targetId, URI uri) throws IOException, InterruptedException {
        Long key = getCombinedIds(srcId, targetId);
        int retries = 10;
        Exception lastException = null;

        while (--retries >= 0) {
            try {
                if(connections.asMap().containsKey(key))
                    log.trace("Reusing cached socket for {} -> {}", sender, uri);

                TCPObjectSocket sock =  connections.get(key, (id) -> {
                    TCPObjectSocket socket = freeSockets.poll();
                    if (null != socket) {
                        try {
                            log.trace("New connection {} to {} id={} srcId={} src={}", socket, uri, id, srcId, sender);
                            socket.wrap(SocketChannel.open(new InetSocketAddress(uri.getHost(), uri.getPort())));
                            socket.setSrcId(srcId);
                            socket.setTargetId(targetId);
                        }
                        catch (IOException e) {
                            try { socket.close(); } catch (Exception ignored) {}
                            freeSockets.offer(socket);
                            throw new RuntimeException(e);
                        }
                    }
                    return socket;
                });

                if (null == sock) {
                    log.trace("No free sockets");
                    /*
                       No free sockets so walk connections to see if any related socket wraps a closed channel
                       Return them and try again
                     */
                    AtomicBoolean done = new AtomicBoolean(false);
                    connections.asMap().forEach((k, v) -> {
                        if (v.isClosed()) {
                            connections.invalidate(k);
                            connections.cleanUp(); // Make sure evictions are done
                            log.trace("Found closed socket for {} .. returning", k);
                            done.set(true);
                        }
                    });
                    /*
                        No luck - so manually evict the oldest and try again
                     */
                    if (!done.get()) {
                        connections.policy().eviction().ifPresent(policy -> {
                            // The first element in the iterator is the "coldest" (LRU)
                            // or next-in-line for eviction
                            var oldestEntry = policy.coldest(1).entrySet().iterator().next();

                            if (oldestEntry != null) {
                                log.trace("Found socket to return: {}", oldestEntry.getKey());
                                connections.invalidate(oldestEntry.getKey());
                                connections.cleanUp(); // Make sure evictions are done
                            } else {
                                log.error("No sockets to return");
                            }
                        });
                    }
                    Thread.sleep(0, 500000); // Allow a little time for any cache manipulation to take root
                                          // In the very rare case this isn't enough and we time out here
                                          // the tell() will retry (handling the IOException)
                    continue;

                }
                else if (sock.isClosed()) {
                  /*
                    Could be closed because we are reusing a cached socket that was closed by the remote server because its
                    WorkerBee got evicted. So clean up and retry
                  */
                    connections.invalidate(key);
                    continue;
                }
                return sock;
            }
            catch (RuntimeException e) {
                lastException = e;
                log.trace("Failed to get connection to: {} [{}]. Retrying.", uri, key);
                connections.invalidate(key);
                try {
                    Thread.sleep(500L);
                } catch (Exception ex) {
                    log.error("Sleep in getSocket was interrupted");
                }
            }
        }
        log.warn("Cannot get connection to remote actor: {} {} {}", uri, key, lastException);
        // Socket is probably cached and damaged - return it to freepool where it will get cleaned up
        // Then let our caller retry
        connections.invalidate(key);
        throw new IOException();
    }

    /**
        Force return of a socket. Usually done by client if a tell fails. This can be because of broken pipes
     *  and invalidating it will close the underlying socket so a retry will work.
     */
    public void returnSocket(int srcId, int targetId) {
        connections.invalidate(getCombinedIds(srcId, targetId));
    }

    private long getCombinedIds(int srcId, int targetId) {
        return (((long) srcId) << 32) | (targetId & 0xFFFFFFFFL);
    }


    /**
     * Takes a TCPObject and associates it with a key
     */
    public static class WrappedTCPObjectSocket {
        final Long key;
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
        public WrappedTCPObjectSocket(Long key, TCPObjectSocket tcpObjectSocket) {
            this.key = key;
            this.tcpObjectSocket = tcpObjectSocket;

        }

    }

}
