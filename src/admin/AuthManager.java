package admin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import util.SecurityUtils;
import logging.ServerLogger;

/**
 * Manages authentication and authorization for the admin interface.
 * 
 * Features:
 * - Username/password authentication
 * - Session management with cookies
 * - SHA-256 password hashing
 * - Session expiration
 * - Optional 2FA support
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class AuthManager {
    
    /** Singleton instance */
    private static AuthManager instance;
    
    /** Active admin sessions (session token -> AdminSession) */
    private final ConcurrentHashMap<String, AdminSession> sessions;
    
    /** Admin credentials (username -> password hash) */
    private final Map<String, String> credentials;
    
    /** Session timeout in milliseconds (1 hour) */
    private static final long SESSION_TIMEOUT_MS = 60 * 60 * 1000;
    
    /** Maximum failed login attempts before lockout */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    
    /** Lockout duration in milliseconds (15 minutes) */
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000;
    
    /** Failed login attempts tracker (IP -> attempts) */
    private final ConcurrentHashMap<String, FailedAttempts> failedAttempts;
    
    /** 2FA enabled flag */
    private boolean twoFactorEnabled;
    
    /** Static 2FA code (for demo purposes) */
    private String staticOtpCode;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /**
     * Represents failed login attempts from an IP.
     */
    private static class FailedAttempts {
        int count;
        long lastAttemptTime;
        long lockoutUntil;
        
        FailedAttempts() {
            this.count = 0;
            this.lastAttemptTime = System.currentTimeMillis();
            this.lockoutUntil = 0;
        }
    }
    
    /**
     * Private constructor for singleton pattern.
     */
    private AuthManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.credentials = new HashMap<>();
        this.failedAttempts = new ConcurrentHashMap<>();
        this.twoFactorEnabled = false;
        this.staticOtpCode = "123456"; // Default static OTP
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Gets the singleton instance.
     * 
     * @return AuthManager instance
     */
    public static synchronized AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }
    
    /**
     * Configures admin credentials.
     * 
     * @param username Admin username
     * @param passwordHash SHA-256 hash of the password
     */
    public void setCredentials(String username, String passwordHash) {
        credentials.put(username, passwordHash);
        logger.logInfo("Admin credentials configured for user: " + username);
    }
    
    /**
     * Enables or disables 2FA.
     * 
     * @param enabled Whether 2FA should be enabled
     * @param staticCode The static OTP code (if enabled)
     */
    public void setTwoFactorAuth(boolean enabled, String staticCode) {
        this.twoFactorEnabled = enabled;
        if (staticCode != null && !staticCode.isEmpty()) {
            this.staticOtpCode = staticCode;
        }
        logger.logInfo("2FA " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Checks if 2FA is enabled.
     * 
     * @return true if 2FA is enabled
     */
    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }
    
    /**
     * Authenticates a user with username and password.
     * 
     * @param username The username
     * @param password The plaintext password
     * @param clientIP The client's IP address
     * @return Session token if successful, null otherwise
     */
    public String authenticate(String username, String password, String clientIP) {
        // Check for lockout
        if (isLockedOut(clientIP)) {
            logger.logError("Login attempt from locked out IP: " + clientIP);
            return null;
        }
        
        // Get stored password hash
        String storedHash = credentials.get(username);
        
        if (storedHash == null) {
            recordFailedAttempt(clientIP);
            logger.logError("Login failed - unknown user: " + username + " from " + clientIP);
            return null;
        }
        
        // Hash the provided password and compare
        String providedHash = SecurityUtils.sha256(password);
        
        if (!storedHash.equals(providedHash)) {
            recordFailedAttempt(clientIP);
            logger.logError("Login failed - invalid password for user: " + username + " from " + clientIP);
            return null;
        }
        
        // If 2FA is enabled, return special marker
        if (twoFactorEnabled) {
            // Return a pending session marker
            return "2FA_PENDING:" + username;
        }
        
        // Clear failed attempts on successful login
        failedAttempts.remove(clientIP);
        
        // Create and return session
        return createSession(username, clientIP);
    }
    
    /**
     * Validates 2FA code and creates session.
     * 
     * @param pendingToken The pending 2FA token
     * @param otpCode The OTP code
     * @param clientIP The client's IP
     * @return Session token if successful, null otherwise
     */
    public String validate2FA(String pendingToken, String otpCode, String clientIP) {
        if (!pendingToken.startsWith("2FA_PENDING:")) {
            return null;
        }
        
        String username = pendingToken.substring(12);
        
        // Validate OTP
        if (!staticOtpCode.equals(otpCode)) {
            logger.logError("2FA validation failed for user: " + username + " from " + clientIP);
            return null;
        }
        
        // Clear failed attempts
        failedAttempts.remove(clientIP);
        
        // Create session
        return createSession(username, clientIP);
    }
    
    /**
     * Creates a new admin session.
     * 
     * @param username The authenticated username
     * @param clientIP The client's IP address
     * @return Session token
     */
    private String createSession(String username, String clientIP) {
        // Generate secure session token
        String sessionToken = SecurityUtils.generateSecureToken(32);
        
        // Create session object
        AdminSession session = new AdminSession(sessionToken, username, clientIP);
        
        // Store session
        sessions.put(sessionToken, session);
        
        logger.logInfo("Admin session created for user: " + username + " from " + clientIP);
        
        return sessionToken;
    }
    
    /**
     * Validates a session token.
     * 
     * @param sessionToken The session token to validate
     * @return true if session is valid
     */
    public boolean validateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isEmpty()) {
            return false;
        }
        
        AdminSession session = sessions.get(sessionToken);
        
        if (session == null) {
            return false;
        }
        
        // Check expiration
        if (session.isExpired(SESSION_TIMEOUT_MS)) {
            sessions.remove(sessionToken);
            return false;
        }
        
        // Update last activity
        session.updateActivity();
        
        return true;
    }
    
    /**
     * Gets the session for a token.
     * 
     * @param sessionToken The session token
     * @return AdminSession, or null if not found/expired
     */
    public AdminSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        
        AdminSession session = sessions.get(sessionToken);
        
        if (session != null && session.isExpired(SESSION_TIMEOUT_MS)) {
            sessions.remove(sessionToken);
            return null;
        }
        
        return session;
    }
    
    /**
     * Invalidates (logs out) a session.
     * 
     * @param sessionToken The session token to invalidate
     */
    public void invalidateSession(String sessionToken) {
        if (sessionToken != null) {
            AdminSession session = sessions.remove(sessionToken);
            if (session != null) {
                logger.logInfo("Admin session invalidated for user: " + session.getUsername());
            }
        }
    }
    
    /**
     * Records a failed login attempt.
     * 
     * @param clientIP The client's IP address
     */
    private void recordFailedAttempt(String clientIP) {
        FailedAttempts attempts = failedAttempts.computeIfAbsent(clientIP, k -> new FailedAttempts());
        attempts.count++;
        attempts.lastAttemptTime = System.currentTimeMillis();
        
        if (attempts.count >= MAX_FAILED_ATTEMPTS) {
            attempts.lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            logger.logError("IP locked out due to too many failed attempts: " + clientIP);
        }
    }
    
    /**
     * Checks if an IP is locked out.
     * 
     * @param clientIP The client's IP address
     * @return true if locked out
     */
    private boolean isLockedOut(String clientIP) {
        FailedAttempts attempts = failedAttempts.get(clientIP);
        
        if (attempts == null) {
            return false;
        }
        
        if (attempts.lockoutUntil > System.currentTimeMillis()) {
            return true;
        }
        
        // Lockout expired, reset
        if (attempts.lockoutUntil > 0) {
            failedAttempts.remove(clientIP);
        }
        
        return false;
    }
    
    /**
     * Cleans up expired sessions.
     */
    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(SESSION_TIMEOUT_MS));
    }
    
    /**
     * Gets the number of active admin sessions.
     * 
     * @return Active session count
     */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }
}
