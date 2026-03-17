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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Main connections between pairs of ActorRefs -- each has an int ID (has of path) and we
 * combine them intop a Long
 */
@Slf4j
public class ActorSystemConnectionManager {

    int CONNECTIONS = 128;

    LinkedBlockingQueue<TCPObjectSocket> freeSockets = new LinkedBlockingQueue<>();

    Cache<Long, TCPObjectSocket> connections = Caffeine
        .newBuilder()
        .maximumSize(CONNECTIONS)
        .removalListener((Long key, TCPObjectSocket socket, RemovalCause cause) -> {
            try {
                socket.send(null);
                socket.close();
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
                    try {
                        // log.info("New connection {} to {} id={} srcId={} src={}", socket, uri, id, srcId, sender);
                        socket.wrap(SocketChannel.open(new InetSocketAddress(uri.getHost(), uri.getPort())));
                        socket.setSrcId(srcId);
                        socket.setTargetId(targetId);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return socket;
                });
                //log.info("Got socket {} for {}->{} [{}] {}", sock, srcId, targetId, key, uri);
                return sock;
            }
            catch (RuntimeException e) {
                log.warn("Failed to get connection to: {}. Retrying.", uri);
                Thread.sleep(3000L);
            }
        }
        log.error("Cannot get connection to remote actor system: {}", uri);
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
