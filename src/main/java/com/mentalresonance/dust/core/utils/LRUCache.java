/*
 *
 *  Copyright 2024-2025 Alan Littleford
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

package com.mentalresonance.dust.core.utils;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A simple LRUCache. This is not thread safe so can be used in Actors without thread-safety overhead
 *
 * @param <A> - key class
 * @param <B> - value class
 */
public class LRUCache<A, B> implements Serializable, Map<A, B> {
    /**
     * maxEntries in cache. Public so we can serialize
     */
    final int maxEntries;

    /**
     * If true the use access order else use insertion order for aging out
     */
    final boolean access;

    /**
     * The underlying cache
     */
    final LinkedHashMap<A, B> _cache;

    /**
     * Defaults to access order
     * @param maxEntries size
     */
    public LRUCache(final int maxEntries) {
        this(maxEntries, true);
    }

    /**
     * Constructor
     * @param maxEntries Size beyond which we start flushin
     * @param access Ordering - 'oldest' is flushed:true for access order, false for insertion order
     */
    public LRUCache(final int maxEntries, boolean access) {
        _cache = new LinkedHashMap<>(maxEntries + 1, 0.75f, access) {
            protected boolean removeEldestEntry(final Map.Entry<A, B> eldest) {
                return super.size() > maxEntries;
            }
        };
        this.maxEntries = maxEntries;
        this.access = access;
    }

    /**
     * Make shallow copy of underlying cache
     * @return LinkedHashMap clone
     */
    public LinkedHashMap<A, B> shallowCopy() {
        return new LinkedHashMap<>(_cache);
    }

    /**
     * Cache size
     * @return number of elements cached
     */
    public int size() {
        return _cache.size();
    }

    /**
     * Is cache empty ?
     * @return True if empty else false
     */
    public boolean isEmpty() {
        return _cache.isEmpty();
    }

    /**
     * Does cache contain key
     * @param key the key
     * @return true if cache has element with this key
     */
    public boolean containsKey(Object key) {
        return _cache.containsKey(key);
    }

    /**
     * Does cache contain value
     * @param value  the value to be test
     * @return true ifd an entry in the map with this value. Test uses both == and equals()
     */
    public boolean containsValue(Object value) {
        return _cache.containsValue(value);
    }

    /**
     * Return the object with given key else null. Note that null is a valid value
     * @param key the key
     * @return the value which may be null. If no key matches then return null.
     */
    public B get(Object key) {
        return _cache.get(key);
    }

    /**
     * Puts the value in the cache under key. Any previous value is replaced.
     * @param key The key
     * @param value The value
     * @return The previous value if it had one else null.
     */
    public B put(A key, B value) {
        return _cache.put(key, value);
    }


    /**
     * Remove object under key if it exists. Returns removed object or null if the key had nothing associated.
     * @param key The key
     * @return removed object or null
     */
    public B remove(Object key) {
        return _cache.remove(key);
    }

    /**
     * Put all elements of the map into the cache
     * @param m the map
     */
    public void putAll(Map<? extends A, ? extends B> m) {
        _cache.putAll(m);
    }

    /**
     * Clear the cache
     */
    public void clear() {  _cache.clear(); }

    /**
     * Return underlying map
     * @return the cache map
     */
    public LinkedHashMap<A, B> map() { return _cache; }

    /**
     * Get keySet of map
     * @return the keySet
     */
    public Set<A> keySet() {
        return _cache.keySet();
    }

    /**
     * Return values in the map
     * @return values
     */
    public Collection<B> values() {
        return _cache.values();
    }

    /**
     * Return EntrySet of the map
     * @return EntrySet
     */
    public Set<Map.Entry<A, B>> entrySet() {
        return _cache.entrySet();
    }
}

