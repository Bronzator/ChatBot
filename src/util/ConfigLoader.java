package util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and manages server configuration.
 * 
 * Configuration file format (server.conf):
 * - Lines starting with # are comments
 * - Key=value format
 * - Environment variables can override config values
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ConfigLoader {
    
    /** Configuration properties */
    private final Properties properties;
    
    /** Config file path */
    private final String configPath;
    
    // Default values
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREAD_POOL_SIZE = 50;
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final String DEFAULT_LOG_FILE = "logs/server.log";
    private static final String DEFAULT_ADMIN_USER = "admin";
    
    /**
     * Constructs a ConfigLoader and loads configuration from file.
     * 
     * @param configPath Path to configuration file
     * @throws IOException If config file cannot be read
     */
    public ConfigLoader(String configPath) throws IOException {
        this.configPath = configPath;
        this.properties = new Properties();
        loadConfig();
    }
    
    /**
     * Loads configuration from file.
     */
    private void loadConfig() throws IOException {
        Path path = Paths.get(configPath);
        
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }
            System.out.println("Configuration loaded from: " + configPath);
        } else {
            System.out.println("Config file not found, using defaults: " + configPath);
        }
        
        // Override with environment variables
        loadEnvironmentOverrides();
    }
    
    /**
     * Loads configuration overrides from environment variables.
     */
    private void loadEnvironmentOverrides() {
        // OpenAI API key
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            properties.setProperty("openai.api_key", apiKey);
        }
        
        // Server port
        String port = System.getenv("SERVER_PORT");
        if (port != null && !port.isEmpty()) {
            properties.setProperty("server.port", port);
        }
        
        // Admin password (for initial setup)
        String adminPass = System.getenv("ADMIN_PASSWORD");
        if (adminPass != null && !adminPass.isEmpty()) {
            // Hash the password
            String hash = SecurityUtils.sha256(adminPass);
            properties.setProperty("admin.password_hash", hash);
        }
    }
    
    // ==================== Server Settings ====================
    
    /**
     * Gets the server port.
     * @return Server port number
     */
    public int getServerPort() {
        return getIntProperty("server.port", DEFAULT_PORT);
    }
    
    /**
     * Gets the thread pool size.
     * @return Thread pool size
     */
    public int getThreadPoolSize() {
        return getIntProperty("server.thread_pool_size", DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Gets the log file path.
     * @return Log file path
     */
    public String getLogFile() {
        return getProperty("server.log_file", DEFAULT_LOG_FILE);
    }
    
    // ==================== Admin Settings ====================
    
    /**
     * Gets the admin username.
     * @return Admin username
     */
    public String getAdminUsername() {
        return getProperty("admin.username", DEFAULT_ADMIN_USER);
    }
    
    /**
     * Gets the admin password hash.
     * @return SHA-256 hash of admin password
     */
    public String getAdminPasswordHash() {
        String hash = getProperty("admin.password_hash", null);
        
        if (hash == null || hash.isEmpty()) {
            // Use default password hash (password = "admin123")
            // In production, this should be configured!
            hash = SecurityUtils.sha256("admin123");
            System.out.println("WARNING: Using default admin password! Change in server.conf");
        }
        
        return hash;
    }
    
    /**
     * Checks if 2FA is enabled.
     * @return true if 2FA is enabled
     */
    public boolean is2FAEnabled() {
        return getBooleanProperty("admin.2fa_enabled", false);
    }
    
    /**
     * Gets the static 2FA code.
     * @return 2FA code or null
     */
    public String get2FACode() {
        return getProperty("admin.2fa_code", "123456");
    }
    
    // ==================== OpenAI Settings ====================
    
    /**
     * Gets the OpenAI API key.
     * @return API key or empty string
     */
    public String getApiKey() {
        return getProperty("openai.api_key", "");
    }
    
    /**
     * Gets the default ChatGPT model.
     * @return Model name
     */
    public String getDefaultModel() {
        return getProperty("openai.default_model", DEFAULT_MODEL);
    }
    
    // ==================== Database Settings ====================
    
    /**
     * Gets the database URL.
     */
    public String getDatabaseUrl() {
        String url = System.getenv("DATABASE_URL");
        if (url != null && !url.isEmpty()) return url;
        return getProperty("database.url", "jdbc:postgresql://localhost:5432/chatbot");
    }
    
    /**
     * Gets the database username.
     */
    public String getDatabaseUsername() {
        String user = System.getenv("DATABASE_USER");
        if (user != null && !user.isEmpty()) return user;
        return getProperty("database.username", "chatbot");
    }
    
    /**
     * Gets the database password.
     */
    public String getDatabasePassword() {
        String pass = System.getenv("DATABASE_PASSWORD");
        if (pass != null && !pass.isEmpty()) return pass;
        return getProperty("database.password", "chatbot");
    }
    
    /**
     * Gets the database connection pool size.
     */
    public int getDatabasePoolSize() {
        return getIntProperty("database.pool_size", 10);
    }
    
    // ==================== JWT Settings ====================
    
    /**
     * Gets the JWT secret key.
     */
    public String getJwtSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret != null && !secret.isEmpty()) return secret;
        return getProperty("jwt.secret", "");
    }
    
    /**
     * Gets JWT access token expiry in milliseconds.
     */
    public long getJwtAccessExpiry(long defaultValue) {
        return getLongProperty("jwt.access_expiry_ms", defaultValue);
    }
    
    /**
     * Gets JWT refresh token expiry in milliseconds.
     */
    public long getJwtRefreshExpiry(long defaultValue) {
        return getLongProperty("jwt.refresh_expiry_ms", defaultValue);
    }
    
    // ==================== Google OAuth Settings ====================
    
    /**
     * Gets Google OAuth client ID.
     */
    public String getGoogleClientId() {
        String id = System.getenv("GOOGLE_CLIENT_ID");
        if (id != null && !id.isEmpty()) return id;
        return getProperty("google.client_id", "");
    }
    
    /**
     * Gets Google OAuth client secret.
     */
    public String getGoogleClientSecret() {
        String secret = System.getenv("GOOGLE_CLIENT_SECRET");
        if (secret != null && !secret.isEmpty()) return secret;
        return getProperty("google.client_secret", "");
    }
    
    /**
     * Gets Google OAuth redirect URI.
     */
    public String getGoogleRedirectUri() {
        return getProperty("google.redirect_uri", "http://localhost:8080/auth/google/callback");
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Gets a string property with default value.
     */
    private String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Gets an integer property with default value.
     */
    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid integer for " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a long property with default value.
     */
    private long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid long for " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a boolean property with default value.
     */
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value.trim().equalsIgnoreCase("true") || value.trim().equals("1");
        }
        return defaultValue;
    }
    
    /**
     * Sets a property value.
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    /**
     * Gets all property keys.
     */
    public Set<String> getKeys() {
        Set<String> keys = new HashSet<>();
        for (Object key : properties.keySet()) {
            keys.add(key.toString());
        }
        return keys;
    }
    
    /**
     * Reloads configuration from file.
     */
    public void reload() throws IOException {
        properties.clear();
        loadConfig();
    }
    
    @Override
    public String toString() {
        return "ConfigLoader{configPath='" + configPath + "', properties=" + properties.size() + "}";
    }
}
