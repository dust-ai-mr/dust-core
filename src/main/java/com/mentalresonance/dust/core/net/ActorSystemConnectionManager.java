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

    int CONNECTIONS = 64;

    LinkedBlockingQueue<TCPObjectSocket> freeSockets = new LinkedBlockingQueue<>();

    Cache<Long, TCPObjectSocket> connections = Caffeine
        .newBuilder()
        .maximumSize(CONNECTIONS)
        .removalListener((Long key, TCPObjectSocket socket, RemovalCause cause) -> {
            try {
                if (! socket.isClosed()) {
                    socket.send(null);
                    socket.close();
                }
                freeSockets.add(socket);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        })
        .build();


    /**
     * Prepare TCPObject sockets since building serializers is expensive
     */
    public ActorSystemConnectionManager() {
        for (int i = 0; i < CONNECTIONS; ++i) {
            freeSockets.add(new TCPObjectSocket());
        }
    }

    public TCPObjectSocket getSocket(ActorRef sender, int srcId, int targetId, URI uri) throws IOException, InterruptedException {
        Long key = getCombinedIds(srcId, targetId);
        int retries = 5;

        while (--retries >= 0) {
            try {
                TCPObjectSocket sock =  connections.get(key, (id) -> {
                    TCPObjectSocket socket = freeSockets.poll();
                    if (null != socket) {
                        try {
                            // log.info("New connection {} to {} id={} srcId={} src={}", socket, uri, id, srcId, sender);
                            socket.wrap(SocketChannel.open(new InetSocketAddress(uri.getHost(), uri.getPort())));
                            socket.setSrcId(srcId);
                            socket.setTargetId(targetId);
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return socket;
                });
                if (null == sock) {
                    /*
                       No free sockets so walk connections to see if any related socket warps a closed channel
                       Return them and try again
                     */
                    AtomicBoolean done = new AtomicBoolean(false);
                    connections.asMap().forEach((k, v) -> {
                        if (v.isClosed()) {
                            connections.invalidate(k);
                            log.info("Found closed socket for {} .. returning", k);
                            done.set(true);
                        }
                    });
                    if (!done.get()) {
                        connections.policy().eviction().ifPresent(policy -> {
                            // The first element in the iterator is the "coldest" (LRU)
                            // or next-in-line for eviction
                            var oldestEntry = policy.coldest(1).entrySet().iterator().next();

                            if (oldestEntry != null) {
                                connections.invalidate(oldestEntry.getKey());
                            }
                        });
                    }
                    Thread.sleep(0, 500); // Allow a little time for any cache maniplation to take root
                                          // In the very rare case this sin't enough and we time out here
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
                log.warn("Failed to get connection to: {}. Retrying.", uri);
                Thread.sleep(3000L);
            }
        }
        log.warn("Cannot get connection to remote actor: {} {}", uri, key);
        throw new IOException();
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
