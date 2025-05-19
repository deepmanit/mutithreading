public class HighPerfConnectionPool implements ConnectionPool {
    private final BlockingQueue<PooledConnection> availableConnections;
    private final ConcurrentHashMap<PooledConnection, Long> allConnections;
    private final Semaphore connectionSemaphore;
    private final ReentrantLock poolLock = new ReentrantLock(true);
    
    // Configuration
    private final int maxPoolSize;
    private final int minIdle;
    private final long maxWaitMillis;
    private final long validationInterval;
    
    public Connection getConnection() throws SQLException {
        // 1. Try to acquire permit with timeout
        if (!connectionSemaphore.tryAcquire(maxWaitMillis, TimeUnit.MILLISECONDS)) {
            throw new SQLTimeoutException("Connection wait timeout");
        }
        
        try {
            PooledConnection conn = null;
            // 2. Try to get existing connection
            conn = availableConnections.poll();
            
            // 3. If none available but pool not full, create new
            if (conn == null && (allConnections.size() < maxPoolSize)) {
                poolLock.lock();
                try {
                    // Double-check under lock
                    if (allConnections.size() < maxPoolSize) {
                        conn = createNewConnection();
                        allConnections.put(conn, System.currentTimeMillis());
                    }
                } finally {
                    poolLock.unlock();
                }
            }
            
            // 4. Validate connection
            if (conn != null && !validateConnection(conn)) {
                disposeConnection(conn);
                conn = null;
            }
            
            // 5. Return validated connection or retry
            return conn != null ? conn : getConnection(); // Recursive retry
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection");
        }
    }
    
    public void releaseConnection(PooledConnection conn) {
        if (conn == null) return;
        
        try {
            if (conn.isClosed()) {
                allConnections.remove(conn);
                connectionSemaphore.release();
            } else if (availableConnections.offer(conn)) {
                allConnections.put(conn, System.currentTimeMillis());
            } else {
                disposeConnection(conn);
            }
        } catch (SQLException e) {
            disposeConnection(conn);
        }
    }
}