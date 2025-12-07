package database;

import java.sql.*;
import java.util.concurrent.*;
import java.util.Properties;
import util.ConfigLoader;
import logging.ServerLogger;

/**
 * Database connection manager with connection pooling.
 * Uses a simple pool implementation for PostgreSQL connections.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class DatabaseManager {
    
    private static DatabaseManager instance;
    private final BlockingQueue<Connection> connectionPool;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;
    private final ServerLogger logger;
    private volatile boolean isShutdown = false;
    
    // Connection settings
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int VALIDATION_TIMEOUT = 3;
    
    /**
     * Private constructor for singleton pattern.
     */
    private DatabaseManager(ConfigLoader config) throws SQLException {
        this.logger = ServerLogger.getInstance();
        
        // Load database configuration
        this.jdbcUrl = config.getDatabaseUrl();
        this.username = config.getDatabaseUsername();
        this.password = config.getDatabasePassword();
        this.poolSize = config.getDatabasePoolSize();
        
        // Initialize connection pool
        this.connectionPool = new LinkedBlockingQueue<>(poolSize);
        
        // Load PostgreSQL driver
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL driver not found", e);
        }
        
        // Create initial connections
        initializePool();
        
        logger.logInfo("Database connection pool initialized with " + poolSize + " connections");
    }
    
    /**
     * Gets the singleton instance.
     */
    public static synchronized DatabaseManager getInstance() {
        return instance;
    }
    
    /**
     * Initializes the database manager with configuration.
     */
    public static synchronized void initialize(ConfigLoader config) throws SQLException {
        if (instance == null) {
            instance = new DatabaseManager(config);
        }
    }
    
    /**
     * Initializes the connection pool.
     */
    private void initializePool() throws SQLException {
        for (int i = 0; i < poolSize; i++) {
            connectionPool.offer(createConnection());
        }
    }
    
    /**
     * Creates a new database connection.
     */
    private Connection createConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", String.valueOf(CONNECTION_TIMEOUT / 1000));
        
        Connection conn = DriverManager.getConnection(jdbcUrl, props);
        conn.setAutoCommit(true);
        return conn;
    }
    
    /**
     * Gets a connection from the pool.
     * 
     * @return Database connection
     * @throws SQLException If no connection available
     */
    public Connection getConnection() throws SQLException {
        if (isShutdown) {
            throw new SQLException("Connection pool is shut down");
        }
        
        try {
            Connection conn = connectionPool.poll(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (conn == null) {
                throw new SQLException("Connection pool exhausted");
            }
            
            // Validate connection
            if (!isConnectionValid(conn)) {
                conn = createConnection();
            }
            
            return conn;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }
    
    /**
     * Returns a connection to the pool.
     * 
     * @param conn Connection to return
     */
    public void releaseConnection(Connection conn) {
        if (conn != null && !isShutdown) {
            try {
                if (!conn.isClosed() && isConnectionValid(conn)) {
                    connectionPool.offer(conn);
                } else {
                    // Replace invalid connection
                    try {
                        connectionPool.offer(createConnection());
                    } catch (SQLException e) {
                        logger.logError("Failed to create replacement connection: " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                logger.logError("Error releasing connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Checks if a connection is valid.
     */
    private boolean isConnectionValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(VALIDATION_TIMEOUT);
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Executes a query with automatic connection management.
     * Returns a ManagedResultSet that should be used with try-with-resources.
     * 
     * @param query SQL query with placeholders
     * @param params Query parameters
     * @return ManagedResultSet (caller must close)
     */
    public ManagedResultSet executeQuery(String query, Object... params) throws SQLException {
        Connection conn = getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            setParameters(stmt, params);
            return new ManagedResultSet(stmt.executeQuery(), stmt, conn, this);
        } catch (SQLException e) {
            releaseConnection(conn);
            throw e;
        }
    }
    
    /**
     * Executes an update with automatic connection management.
     * 
     * @param query SQL query with placeholders
     * @param params Query parameters
     * @return Number of affected rows
     */
    public int executeUpdate(String query, Object... params) throws SQLException {
        Connection conn = getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            setParameters(stmt, params);
            int result = stmt.executeUpdate();
            stmt.close();
            return result;
        } finally {
            releaseConnection(conn);
        }
    }
    
    /**
     * Executes an insert and returns generated keys.
     * 
     * @param query SQL insert query
     * @param params Query parameters
     * @return Generated key (UUID as string)
     */
    public String executeInsert(String query, Object... params) throws SQLException {
        Connection conn = getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            setParameters(stmt, params);
            stmt.executeUpdate();
            
            ResultSet keys = stmt.getGeneratedKeys();
            String generatedId = null;
            if (keys.next()) {
                generatedId = keys.getString(1);
            }
            keys.close();
            stmt.close();
            return generatedId;
        } finally {
            releaseConnection(conn);
        }
    }
    
    /**
     * Sets parameters on a prepared statement.
     */
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (param instanceof String) {
                stmt.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                stmt.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                stmt.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                stmt.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                stmt.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof Timestamp) {
                stmt.setTimestamp(i + 1, (Timestamp) param);
            } else if (param instanceof java.util.UUID) {
                stmt.setObject(i + 1, param);
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }
    
    /**
     * Shuts down the connection pool.
     */
    public void shutdown() {
        isShutdown = true;
        logger.logInfo("Shutting down database connection pool...");
        
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // Ignore close errors during shutdown
            }
        }
        
        logger.logInfo("Database connection pool shut down");
    }
    
    /**
     * Gets pool statistics.
     */
    public int getAvailableConnections() {
        return connectionPool.size();
    }
    
    /**
     * Wrapper for ResultSet that auto-releases connection when closed.
     */
    public static class ManagedResultSet implements AutoCloseable {
        private final ResultSet resultSet;
        private final PreparedStatement statement;
        private final Connection connection;
        private final DatabaseManager manager;
        
        ManagedResultSet(ResultSet rs, PreparedStatement stmt, Connection conn, DatabaseManager mgr) {
            this.resultSet = rs;
            this.statement = stmt;
            this.connection = conn;
            this.manager = mgr;
        }
        
        public ResultSet getResultSet() {
            return resultSet;
        }
        
        // Delegate common ResultSet methods
        public boolean next() throws SQLException {
            return resultSet.next();
        }
        
        public String getString(String columnLabel) throws SQLException {
            return resultSet.getString(columnLabel);
        }
        
        public String getString(int columnIndex) throws SQLException {
            return resultSet.getString(columnIndex);
        }
        
        public int getInt(String columnLabel) throws SQLException {
            return resultSet.getInt(columnLabel);
        }
        
        public int getInt(int columnIndex) throws SQLException {
            return resultSet.getInt(columnIndex);
        }
        
        public long getLong(String columnLabel) throws SQLException {
            return resultSet.getLong(columnLabel);
        }
        
        public boolean getBoolean(String columnLabel) throws SQLException {
            return resultSet.getBoolean(columnLabel);
        }
        
        public Timestamp getTimestamp(String columnLabel) throws SQLException {
            return resultSet.getTimestamp(columnLabel);
        }
        
        public Object getObject(String columnLabel) throws SQLException {
            return resultSet.getObject(columnLabel);
        }
        
        @Override
        public void close() {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
            } catch (SQLException e) {
                // Ignore
            } finally {
                manager.releaseConnection(connection);
            }
        }
    }
}
