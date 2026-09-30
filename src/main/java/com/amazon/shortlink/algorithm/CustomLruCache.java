package com.amazon.shortlink.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, thread-safe LRU Cache built from scratch using
 * a HashMap and a Doubly Linked List with sentinel nodes.
 * 
 * Tracks telemetry (hit count, miss count, eviction count, hit ratio)
 * for observability.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class CustomLruCache<K, V> {

    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    private final ReentrantLock lock = new ReentrantLock();

    // Telemetry counters
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    public CustomLruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);

        // Sentinel dummy nodes to eliminate boundary null checks
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    /**
     * Retrieves an item from the cache and marks it as most recently used.
     *
     * @param key Key to search
     * @return Cached value, or null if miss
     */
    public V get(K key) {
        if (key == null) {
            return null;
        }

        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) {
                missCount.incrementAndGet();
                return null;
            }

            // Hit: promote node to head (most recently used)
            moveToHead(node);
            hitCount.incrementAndGet();
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts or updates an item in the cache.
     * If capacity is reached, evicts the least recently used item (tail.prev).
     *
     * @param key   Key
     * @param value Value
     */
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }

        lock.lock();
        try {
            Node<K, V> node = map.get(key);
            if (node != null) {
                // Key exists: update value and promote to head
                node.value = value;
                moveToHead(node);
            } else {
                // New entry: check capacity
                if (map.size() >= capacity) {
                    Node<K, V> lru = removeTail();
                    if (lru != null) {
                        map.remove(lru.key);
                        evictionCount.incrementAndGet();
                    }
                }
                Node<K, V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                addToHead(newNode);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Explicitly removes a key from the cache.
     *
     * @param key Key to evict
     * @return true if found and removed; false otherwise
     */
    public boolean remove(K key) {
        if (key == null) {
            return false;
        }

        lock.lock();
        try {
            Node<K, V> node = map.remove(key);
            if (node != null) {
                unlink(node);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears all cached items and resets size.
     */
    public void clear() {
        lock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEvictionCount() {
        return evictionCount.get();
    }

    /**
     * Returns the hit ratio between 0.0 and 1.0.
     */
    public double getHitRatio() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }

    // Doubly linked list internal operations (called under lock)

    private void addToHead(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) {
        unlink(node);
        addToHead(node);
    }

    private Node<K, V> removeTail() {
        if (tail.prev == head) {
            return null; // Cache is empty
        }
        Node<K, V> lru = tail.prev;
        unlink(lru);
        return lru;
    }
}
