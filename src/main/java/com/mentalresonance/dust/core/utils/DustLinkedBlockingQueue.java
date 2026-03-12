/*
 * Copyright 2024-2025 Alan Littleford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package com.mentalresonance.dust.core.utils;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Single-Consumer optimized variant of LinkedBlockingQueue.
 * * This version allows the user to access takeLock/putLock publicly to facilitate
 * atomic "unstashing" operations, while minimizing locking overhead for the
 * consumer thread.
 */
public class DustLinkedBlockingQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {

    private static final long serialVersionUID = -6903933977591709194L;

    static class Node<E> {
        E item;
        Node<E> next;
        Node(E x) { item = x; }
    }

    private final int capacity;
    private final AtomicInteger count = new AtomicInteger();

    transient Node<E> head;
    private transient Node<E> last;

    public final ReentrantLock takeLock = new ReentrantLock();
    private final Condition notEmpty = takeLock.newCondition();

    public final ReentrantLock putLock = new ReentrantLock();
    private final Condition notFull = putLock.newCondition();

    public DustLinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        last = head = new Node<E>(null);
    }

    public DustLinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    // --- Internal Helpers ---

    private void signalNotEmpty() {
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    private void signalNotFull() {
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    private void enqueue(Node<E> node) {
        last = last.next = node;
    }

    private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h;
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    public void fullyLock() {
        putLock.lock();
        //takeLock.lock();
    }

    public void fullyUnlock() {
        //takeLock.unlock();
        putLock.unlock();
    }

    // --- BlockingQueue Methods ---

    @Override
    public int size() {
        return count.get();
    }

    @Override
    public int remainingCapacity() {
        return capacity - count.get();
    }

    @Override
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        int c = -1;
        Node<E> node = new Node<>(e);
        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                notFull.await();
            }
            enqueue(node);
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0) signalNotEmpty();
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int c = -1;
        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                if (nanos <= 0L) return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(new Node<>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0) signalNotEmpty();
        return true;
    }

    @Override
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        if (count.get() == capacity) return false;
        int c = -1;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                enqueue(new Node<>(e));
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) signalNotEmpty();
        return c >= 0;
    }

    @Override
    public E take() throws InterruptedException {
        E x;
        int c;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1) notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) signalNotFull();
        return x;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        E x = null;
        int c = -1;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0L) return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1) notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) signalNotFull();
        return x;
    }

    @Override
    public E poll() {
        if (count.get() == 0) return null;
        E x = null;
        int c = -1;
        takeLock.lock();
        try {
            if (count.get() > 0) {
                x = dequeue();
                c = count.getAndDecrement();
                if (c > 1) notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) signalNotFull();
        return x;
    }

    @Override
    public E peek() {
        if (count.get() == 0) return null;
        takeLock.lock();
        try {
            Node<E> first = head.next;
            return (first == null) ? null : first.item;
        } finally {
            takeLock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this) throw new IllegalArgumentException();
        fullyLock();
        try {
            int n = Math.min(maxElements, count.get());
            Node<E> h = head;
            int i = 0;
            while (i < n) {
                Node<E> p = h.next;
                c.add(p.item);
                p.item = null;
                h.next = h;
                h = p;
                i++;
            }
            if (i > 0) {
                head = h;
                if (count.getAndAdd(-i) == capacity)
                    notFull.signal();
            }
            return n;
        } finally {
            fullyUnlock();
        }
    }

    // --- Iterator Implementation ---

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        private Node<E> current;
        private E currentItem;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null) currentItem = current.item;
            } finally {
                fullyUnlock();
            }
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public E next() {
            fullyLock();
            try {
                if (current == null) throw new NoSuchElementException();
                E x = currentItem;
                current = current.next;
                currentItem = (current != null) ? current.item : null;
                return x;
            } finally {
                fullyUnlock();
            }
        }
    }
}