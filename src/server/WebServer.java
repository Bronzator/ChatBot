package server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import logging.ServerLogger;
import util.ConfigLoader;

/**
 * Main HTTP Web Server class.
 * 
 * This class is responsible for:
 * - Starting the server on a configurable port
 * - Accepting incoming client connections
 * - Managing a thread pool for concurrent request handling
 * - Tracking server statistics (uptime, request count, etc.)
 * 
 * The server supports GET, HEAD, and POST HTTP methods, and provides
 * a ChatGPT API wrapper, static file serving, and an admin interface.
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class WebServer {
    
    /** Server socket for accepting connections */
    private ServerSocket serverSocket;
    
    /** Thread pool for handling client requests */
    private ExecutorService threadPool;
    
    /** Configuration loader instance */
    private final ConfigLoader config;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /** Server running state */
    private volatile boolean running;
    
    /** Server start time for uptime calculation */
    private final long startTime;
    
    /** Total request counter (thread-safe) */
    private final AtomicLong totalRequests;
    
    /** Active connection counter (thread-safe) */
    private final AtomicInteger activeConnections;
    
    /** Chat enabled flag (can be toggled by admin) */
    private volatile boolean chatEnabled;
    
    /** Current ChatGPT model */
    private volatile String currentModel;
    
    /** Singleton instance */
    private static WebServer instance;
    
    /**
     * Constructs a new WebServer with the given configuration.
     * 
     * @param config The configuration loader containing server settings
     */
    public WebServer(ConfigLoader config) {
        this.config = config;
        this.logger = ServerLogger.getInstance();
        this.startTime = System.currentTimeMillis();
        this.totalRequests = new AtomicLong(0);
        this.activeConnections = new AtomicInteger(0);
        this.chatEnabled = true;
        this.currentModel = config.getDefaultModel();
        this.running = false;
        instance = this;
    }
    
    /**
     * Gets the singleton instance of WebServer.
     * 
     * @return The WebServer instance
     */
    public static WebServer getInstance() {
        return instance;
    }
    
    /**
     * Starts the web server.
     * 
     * Creates a server socket on the configured port and begins
     * accepting client connections. Each connection is handled
     * by a ClientHandler in a separate thread from the pool.
     * 
     * @throws IOException If the server socket cannot be created
     */
    public void start() throws IOException {
        int port = config.getServerPort();
        int poolSize = config.getThreadPoolSize();
        
        serverSocket = new ServerSocket(port);
        threadPool = Executors.newFixedThreadPool(poolSize);
        running = true;
        
        logger.logInfo("WebServer started on port " + port);
        logger.logInfo("Thread pool size: " + poolSize);
        logger.logInfo("Default ChatGPT model: " + currentModel);
        
        System.out.println("========================================");
        System.out.println("  ChatGPT Web Server Started");
        System.out.println("  Port: " + port);
        System.out.println("  http://localhost:" + port);
        System.out.println("========================================");
        
        // Main accept loop
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                activeConnections.incrementAndGet();
                totalRequests.incrementAndGet();
                
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
                
            } catch (SocketException e) {
                if (running) {
                    logger.logError("Socket error: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Stops the web server gracefully.
     * 
     * Closes the server socket, shuts down the thread pool,
     * and waits for active handlers to complete.
     */
    public void stop() {
        running = false;
        logger.logInfo("WebServer shutting down...");
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            if (threadPool != null) {
                threadPool.shutdown();
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            }
            
            logger.logInfo("WebServer stopped");
            
        } catch (IOException | InterruptedException e) {
            logger.logError("Error during shutdown: " + e.getMessage());
        }
    }
    
    /**
     * Decrements the active connection counter.
     * Called by ClientHandler when a connection is closed.
     */
    public void connectionClosed() {
        activeConnections.decrementAndGet();
    }
    
    // ==================== Getters ====================
    
    /**
     * Gets the server uptime in milliseconds.
     * @return Uptime in milliseconds
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Gets the total number of requests processed.
     * @return Total request count
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    /**
     * Gets the current number of active connections.
     * @return Active connection count
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }
    
    /**
     * Checks if chat functionality is enabled.
     * @return true if chat is enabled
     */
    public boolean isChatEnabled() {
        return chatEnabled;
    }
    
    /**
     * Sets the chat enabled state.
     * @param enabled true to enable, false to disable
     */
    public void setChatEnabled(boolean enabled) {
        this.chatEnabled = enabled;
        logger.logInfo("Chat " + (enabled ? "enabled" : "disabled") + " by admin");
    }
    
    /**
     * Gets the current ChatGPT model.
     * @return Current model name
     */
    public String getCurrentModel() {
        return currentModel;
    }
    
    /**
     * Sets the current ChatGPT model.
     * @param model The model name to use
     */
    public void setCurrentModel(String model) {
        this.currentModel = model;
        logger.logInfo("ChatGPT model changed to: " + model);
    }
    
    /**
     * Gets the configuration loader.
     * @return ConfigLoader instance
     */
    public ConfigLoader getConfig() {
        return config;
    }
    
    /**
     * Gets the logger instance.
     * @return ServerLogger instance
     */
    public ServerLogger getLogger() {
        return logger;
    }
    
    /**
     * Checks if the server is running.
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments (optional config file path)
     */
    public static void main(String[] args) {
        try {
            // Load configuration
            String configPath = args.length > 0 ? args[0] : "config/server.conf";
            ConfigLoader config = new ConfigLoader(configPath);
            
            // Initialize logger
            ServerLogger.getInstance().initialize(config.getLogFile());
            
            // Create and start server
            WebServer server = new WebServer(config);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down server...");
                server.stop();
            }));
            
            server.start();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
