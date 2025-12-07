package logging;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server logging system.
 * 
 * Features:
 * - Timestamp-formatted log entries
 * - File-based logging with rotation
 * - In-memory recent logs for admin API
 * - Thread-safe logging
 * - Different log levels (INFO, ERROR, REQUEST, RESPONSE)
 * 
 * Log format: [Timestamp] [Level] [IP] [Method] [Path] [Message]
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ServerLogger {
    
    /** Singleton instance */
    private static ServerLogger instance;
    
    /** Log file path */
    private String logFilePath;
    
    /** Writer for log file */
    private PrintWriter logWriter;
    
    /** In-memory log buffer (circular buffer) */
    private final LinkedList<String> recentLogs;
    
    /** Maximum number of recent logs to keep in memory */
    private static final int MAX_RECENT_LOGS = 1000;
    
    /** Date format for log entries */
    private static final SimpleDateFormat LOG_DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /** Lock for thread-safe logging */
    private final Object lock = new Object();
    
    /** Whether logger is initialized */
    private boolean initialized = false;
    
    /**
     * Private constructor for singleton pattern.
     */
    private ServerLogger() {
        this.recentLogs = new LinkedList<>();
    }
    
    /**
     * Gets the singleton instance.
     * 
     * @return ServerLogger instance
     */
    public static synchronized ServerLogger getInstance() {
        if (instance == null) {
            instance = new ServerLogger();
        }
        return instance;
    }
    
    /**
     * Initializes the logger with a log file path.
     * 
     * @param logFilePath Path to the log file
     */
    public void initialize(String logFilePath) {
        synchronized (lock) {
            this.logFilePath = logFilePath;
            
            try {
                // Create log directory if it doesn't exist
                Path logPath = Paths.get(logFilePath);
                Path parentDir = logPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                
                // Open log file for appending
                FileWriter fw = new FileWriter(logFilePath, true);
                this.logWriter = new PrintWriter(new BufferedWriter(fw), true);
                this.initialized = true;
                
                logInfo("Logger initialized - log file: " + logFilePath);
                
            } catch (IOException e) {
                System.err.println("Failed to initialize log file: " + e.getMessage());
                this.initialized = false;
            }
        }
    }
    
    /**
     * Logs an informational message.
     * 
     * @param message The message to log
     */
    public void logInfo(String message) {
        log("INFO", null, null, null, message);
    }
    
    /**
     * Logs an error message.
     * 
     * @param message The error message
     */
    public void logError(String message) {
        log("ERROR", null, null, null, message);
    }
    
    /**
     * Logs an incoming HTTP request.
     * 
     * @param ip Client IP address
     * @param method HTTP method
     * @param path Request path
     */
    public void logRequest(String ip, String method, String path) {
        log("REQUEST", ip, method, path, null);
    }
    
    /**
     * Logs an HTTP response.
     * 
     * @param ip Client IP address
     * @param method HTTP method
     * @param path Request path
     * @param statusCode HTTP status code
     */
    public void logResponse(String ip, String method, String path, int statusCode) {
        log("RESPONSE", ip, method, path, String.valueOf(statusCode));
    }
    
    /**
     * Logs an admin action.
     * 
     * @param username Admin username
     * @param action The action performed
     */
    public void logAdminAction(String username, String action) {
        log("ADMIN", null, null, null, username + " - " + action);
    }
    
    /**
     * Logs a chat session event.
     * 
     * @param sessionId Chat session ID
     * @param event The event (created, completed, etc.)
     */
    public void logChatEvent(String sessionId, String event) {
        log("CHAT", null, null, null, sessionId + " - " + event);
    }
    
    /**
     * Core logging method.
     * 
     * @param level Log level
     * @param ip Client IP (optional)
     * @param method HTTP method (optional)
     * @param path Request path (optional)
     * @param message Additional message (optional)
     */
    private void log(String level, String ip, String method, String path, String message) {
        synchronized (lock) {
            // Format timestamp
            String timestamp = LOG_DATE_FORMAT.format(new Date());
            
            // Build log entry
            StringBuilder entry = new StringBuilder();
            entry.append("[").append(timestamp).append("]");
            entry.append(" [").append(level).append("]");
            
            if (ip != null) {
                entry.append(" [").append(ip).append("]");
            }
            
            if (method != null) {
                entry.append(" [").append(method).append("]");
            }
            
            if (path != null) {
                entry.append(" [").append(path).append("]");
            }
            
            if (message != null) {
                entry.append(" ").append(message);
            }
            
            String logEntry = entry.toString();
            
            // Add to recent logs (circular buffer)
            recentLogs.addLast(logEntry);
            if (recentLogs.size() > MAX_RECENT_LOGS) {
                recentLogs.removeFirst();
            }
            
            // Write to file
            if (initialized && logWriter != null) {
                logWriter.println(logEntry);
            }
            
            // Also print to console
            if (level.equals("ERROR")) {
                System.err.println(logEntry);
            } else {
                System.out.println(logEntry);
            }
        }
    }
    
    /**
     * Gets recent log entries.
     * 
     * @param lines Number of lines to return
     * @return Recent log entries as string
     */
    public String getRecentLogs(int lines) {
        synchronized (lock) {
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, recentLogs.size() - lines);
            
            for (int i = start; i < recentLogs.size(); i++) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(recentLogs.get(i));
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Gets recent log entries as a list.
     * 
     * @param lines Number of lines to return
     * @return List of recent log entries
     */
    public List<String> getRecentLogsList(int lines) {
        synchronized (lock) {
            int start = Math.max(0, recentLogs.size() - lines);
            return new ArrayList<>(recentLogs.subList(start, recentLogs.size()));
        }
    }
    
    /**
     * Clears all in-memory logs.
     */
    public void clearLogs() {
        synchronized (lock) {
            recentLogs.clear();
            logInfo("Logs cleared");
        }
    }
    
    /**
     * Rotates the log file.
     * Creates a backup and starts fresh.
     */
    public void rotateLogFile() {
        synchronized (lock) {
            if (!initialized || logFilePath == null) {
                return;
            }
            
            try {
                // Close current writer
                if (logWriter != null) {
                    logWriter.close();
                }
                
                // Rename current log file
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String backupPath = logFilePath + "." + timestamp;
                
                Path source = Paths.get(logFilePath);
                Path target = Paths.get(backupPath);
                
                if (Files.exists(source)) {
                    Files.move(source, target);
                }
                
                // Open new log file
                FileWriter fw = new FileWriter(logFilePath, true);
                this.logWriter = new PrintWriter(new BufferedWriter(fw), true);
                
                logInfo("Log file rotated - backup: " + backupPath);
                
            } catch (IOException e) {
                System.err.println("Failed to rotate log file: " + e.getMessage());
            }
        }
    }
    
    /**
     * Closes the logger and releases resources.
     */
    public void close() {
        synchronized (lock) {
            if (logWriter != null) {
                logInfo("Logger shutting down");
                logWriter.close();
                logWriter = null;
            }
            initialized = false;
        }
    }
    
    /**
     * Gets the total number of log entries in memory.
     * 
     * @return Number of log entries
     */
    public int getLogCount() {
        synchronized (lock) {
            return recentLogs.size();
        }
    }
}
