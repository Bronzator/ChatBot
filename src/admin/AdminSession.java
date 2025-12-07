package admin;

/**
 * Represents an admin session.
 * 
 * Contains:
 * - Session token
 * - Username
 * - Client IP
 * - Timestamps for creation and last activity
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class AdminSession {
    
    /** Unique session token */
    private final String token;
    
    /** Username of the authenticated admin */
    private final String username;
    
    /** Client IP address */
    private final String clientIP;
    
    /** Session creation timestamp */
    private final long createdAt;
    
    /** Last activity timestamp */
    private long lastActivityAt;
    
    /**
     * Constructs a new AdminSession.
     * 
     * @param token Session token
     * @param username Authenticated username
     * @param clientIP Client IP address
     */
    public AdminSession(String token, String username, String clientIP) {
        this.token = token;
        this.username = username;
        this.clientIP = clientIP;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
    }
    
    /**
     * Checks if the session has expired.
     * 
     * @param timeoutMs Timeout in milliseconds
     * @return true if expired
     */
    public boolean isExpired(long timeoutMs) {
        return (System.currentTimeMillis() - lastActivityAt) > timeoutMs;
    }
    
    /**
     * Updates the last activity timestamp.
     */
    public void updateActivity() {
        this.lastActivityAt = System.currentTimeMillis();
    }
    
    // ==================== Getters ====================
    
    public String getToken() {
        return token;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getClientIP() {
        return clientIP;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getLastActivityAt() {
        return lastActivityAt;
    }
    
    /**
     * Gets session duration in milliseconds.
     * @return Duration since creation
     */
    public long getDuration() {
        return System.currentTimeMillis() - createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("AdminSession{user='%s', ip='%s', created=%d}",
            username, clientIP, createdAt);
    }
}
