import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadSafeLRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final DoublyLinkedList<K, V> list;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public ThreadSafeLRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.list = new DoublyLinkedList<>();
    }

    public V get(K key) {
        readLock.lock();
        try {
            Node<K, V> node = cache.get(key);
            if (node == null) {
                return null;
            }
            // Move accessed node to front of list (most recently used)
            writeLock.lock();
            try {
                list.moveToHead(node);
            } finally {
                writeLock.unlock();
            }
            return node.value;
        } finally {
            readLock.lock();
        }
    }

    public void put(K key, V value) {
        writeLock.lock();
        try {
            Node<K, V> node = cache.get(key);
            
            if (node != null) {
                // Update existing node
                node.value = value;
                list.moveToHead(node);
            } else {
                // Create new node
                Node<K, V> newNode = new Node<>(key, value);
                
                if (cache.size() >= capacity) {
                    // Remove least recently used
                    Node<K, V> tail = list.removeTail();
                    cache.remove(tail.key);
                }
                
                // Add new node
                list.addToHead(newNode);
                cache.put(key, newNode);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }

    public boolean containsKey(K key) {
        readLock.lock();
        try {
            return cache.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    // Doubly linked list node
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

    // Doubly linked list to maintain access order
    private static class DoublyLinkedList<K, V> {
        private Node<K, V> head;
        private Node<K, V> tail;

        void addToHead(Node<K, V> node) {
            if (head == null) {
                head = tail = node;
            } else {
                node.next = head;
                head.prev = node;
                head = node;
            }
        }

        void removeNode(Node<K, V> node) {
            if (node.prev != null) {
                node.prev.next = node.next;
            } else {
                head = node.next;
            }

            if (node.next != null) {
                node.next.prev = node.prev;
            } else {
                tail = node.prev;
            }
        }

        void moveToHead(Node<K, V> node) {
            removeNode(node);
            addToHead(node);
        }

        Node<K, V> removeTail() {
            if (tail == null) {
                return null;
            }
            Node<K, V> removed = tail;
            removeNode(tail);
            return removed;
        }
    }
}