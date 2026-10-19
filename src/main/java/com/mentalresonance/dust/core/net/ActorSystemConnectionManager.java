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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Reusable, leased connections between (source actor, target actor) pairs. */
@Slf4j
public class ActorSystemConnectionManager {

    private static final int ACQUIRE_RETRIES = 100;
    private static final long RETRY_DELAY_MS = 1;

    private final LinkedBlockingQueue<TCPObjectSocket> freeSockets = new LinkedBlockingQueue<>();
    private final Cache<Long, ManagedSocket> connections;

    public ActorSystemConnectionManager(int size, List<Class<?>> registeredClasses) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");

        connections = Caffeine.newBuilder()
                .maximumSize(size)
                .removalListener((Long key, ManagedSocket entry, RemovalCause cause) -> {
                    if (entry != null) {
                        log.trace("Retiring socket for key {} ({})", key, cause);
                        entry.retire();
                    }
                })
                .build();

        for (int i = 0; i < size; i++) {
            freeSockets.add(new TCPObjectSocket(registeredClasses));
        }
    }

    /**
     * Borrow a connection until the returned lease is closed. Every successful
     * acquire must be paired with a close (prefer try-with-resources).
     */
    public SocketLease acquireSocket(ActorRef sender, int srcId, int targetId, URI uri)
            throws IOException, InterruptedException {
        long key = getCombinedIds(srcId, targetId);
        IOException lastError = null;

        for (int attempt = 0; attempt < ACQUIRE_RETRIES; attempt++) {
            AtomicReference<ManagedSocket> borrowed = new AtomicReference<>();
            try {
                // Per-key atomic operation: removal cannot take the entry out
                // of the cache between finding it and acquiring its lease.
                connections.asMap().compute(key, (id, current) -> {
                    if (current != null) {
                        if (current.acquire()) {
                            borrowed.set(current);
                            return current;
                        }
                        return null; // Already retired; remove stale mapping.
                    }

                    TCPObjectSocket socket = freeSockets.poll();
                    if (socket == null) return null;

                    try {
                        socket.wrap(SocketChannel.open(
                                new InetSocketAddress(uri.getHost(), uri.getPort())));
                        if (socket.isClosed()) {
                            throw new IOException("Socket failed to initialize for " + uri);
                        }
                        socket.setSrcId(srcId);
                        socket.setTargetId(targetId);

                        ManagedSocket fresh = new ManagedSocket(socket);
                        fresh.acquire();
                        borrowed.set(fresh);
                        log.trace("Connected {} to {} [key={}]", sender, uri, id);
                        return fresh;
                    } catch (IOException e) {
                        socket.close();
                        freeSockets.offer(socket);
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                lastError = e.getCause();
            }

            ManagedSocket entry = borrowed.get();
            if (entry != null) {
                if (!entry.socket.isClosed()) {
                    return new SocketLease(key, entry);
                }
                // A stale/closed connection; remove by identity, then release.
                removeAndRetire(key, entry);
                entry.release();
            } else if (lastError == null) {
                // No unused pool socket. Retire a cached connection to free one.
                evictOneIdleCandidate();
            }
            Thread.sleep(RETRY_DELAY_MS);
        }

        throw new IOException("Unable to acquire socket for " + uri, lastError);
    }

    /**
     * Mark only the exact borrowed connection as bad. A newer replacement with
     * the same key cannot accidentally be invalidated.
     */
    public void invalidate(SocketLease lease) {
        removeAndRetire(lease.key, lease.entry);
    }

    /**
     * Only the thread that successfully removes this precise cache mapping
     * may retire it immediately. The Caffeine removal listener is a fallback
     * for automatic eviction and is harmless if it fires later.
     */
    private boolean removeAndRetire(long key, ManagedSocket entry) {
        if (!connections.asMap().remove(key, entry)) return false;
        entry.retire();
        return true;
    }

    /**
     * First recycle a closed or unleased cached connection. Only if no idle
     * candidate exists do we retire a leased connection; its cleanup then
     * waits until its final borrower releases it.
     *
     * Do not hold a cache compute lock while doing socket I/O or recycling.
     */
    private boolean evictOneIdleCandidate() {
        // Prefer an already closed connection. Conditional removal prevents
        // retiring a replacement mapped to the same key.
        for (Map.Entry<Long, ManagedSocket> e : connections.asMap().entrySet()) {
            ManagedSocket entry = e.getValue();
            if (entry.socket.isClosed() && removeAndRetire(e.getKey(), entry))
                return true;
        }

        var eviction = connections.policy().eviction();
        if (eviction.isEmpty()) return false;

        // Caffeine's coldest(n) is a policy snapshot, not strict LRU.
        // Examine multiple candidates so an actively leased cold entry does
        // not force deferred recycling while idle entries remain.
        var candidates = eviction.get().coldest(connections.asMap().size());
        for (Map.Entry<Long, ManagedSocket> e : candidates.entrySet()) {
            if (e.getValue().isIdle() && removeAndRetire(e.getKey(), e.getValue()))
                return true;
        }

        // All observed candidates are busy. Retire one anyway; its last
        // borrower will recycle it when released.
        for (Map.Entry<Long, ManagedSocket> e : candidates.entrySet()) {
            if (removeAndRetire(e.getKey(), e.getValue())) return true;
        }
        return false;
    }

    private static long getCombinedIds(int srcId, int targetId) {
        return ((long) srcId << 32) | (targetId & 0xFFFFFFFFL);
    }

    public final class SocketLease implements AutoCloseable {
        private final long key;
        private final ManagedSocket entry;
        private boolean released;

        private SocketLease(long key, ManagedSocket entry) {
            this.key = key;
            this.entry = entry;
        }

        public TCPObjectSocket socket() {
            if (released) throw new IllegalStateException("Socket lease already closed");
            return entry.socket;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                entry.release();
            }
        }
    }

    private final class ManagedSocket {
        private final TCPObjectSocket socket;
        private int borrowers;
        private boolean retired;
        private boolean cleaned;

        private ManagedSocket(TCPObjectSocket socket) {
            this.socket = socket;
        }

        private synchronized boolean acquire() {
            if (retired) return false;
            borrowers++;
            return true;
        }

        private synchronized boolean isIdle() {
            return borrowers == 0 && !retired;
        }

        private void release() {
            boolean cleanup;
            synchronized (this) {
                if (borrowers <= 0) throw new IllegalStateException("Unbalanced socket release");
                borrowers--;
                cleanup = claimCleanupIfReady();
            }
            if (cleanup) recycle();
        }

        private void retire() {
            boolean cleanup;
            synchronized (this) {
                retired = true;
                cleanup = claimCleanupIfReady();
            }
            if (cleanup) recycle();
        }

        /** Called only under this object's monitor. */
        private boolean claimCleanupIfReady() {
            if (retired && borrowers == 0 && !cleaned) {
                cleaned = true;
                return true;
            }
            return false;
        }

        private void recycle() {
            try {
                if (!socket.isClosed()) socket.send(null); // Remote WorkerBee termination signal.
            } catch (Exception e) {
                log.debug("Could not send connection termination frame: {}", e.toString());
            } finally {
                socket.close();
                freeSockets.offer(socket);
            }
        }
    }
}
