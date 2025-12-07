package database;

import java.sql.*;
import java.util.*;
import logging.ServerLogger;

/**
 * Data Access Object for User operations.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class UserDAO {
    
    private final DatabaseManager db;
    private final ServerLogger logger;
    
    public UserDAO() {
        this.db = DatabaseManager.getInstance();
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Creates a new user with local authentication.
     * 
     * @param email User email
     * @param username Username
     * @param passwordHash SHA-256 hashed password
     * @param displayName Display name
     * @return Created user or null if failed
     */
    public User createUser(String email, String username, String passwordHash, String displayName) {
        String query = """
            INSERT INTO users (email, username, password_hash, display_name, auth_provider)
            VALUES (?, ?, ?, ?, 'local')
            RETURNING id, created_at
            """;
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, email.toLowerCase());
                stmt.setString(2, username);
                stmt.setString(3, passwordHash);
                stmt.setString(4, displayName != null ? displayName : username);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getString("id"));
                    user.setEmail(email.toLowerCase());
                    user.setUsername(username);
                    user.setDisplayName(displayName != null ? displayName : username);
                    user.setAuthProvider("local");
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    
                    rs.close();
                    stmt.close();
                    logger.logInfo("Created new user: " + username);
                    return user;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to create user: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Creates or updates a user from Google OAuth.
     * 
     * @param googleId Google user ID
     * @param email User email
     * @param displayName Display name from Google
     * @param avatarUrl Profile picture URL
     * @return User object
     */
    public User createOrUpdateGoogleUser(String googleId, String email, String displayName, String avatarUrl) {
        // First, try to find existing user
        User existing = findByGoogleId(googleId);
        if (existing != null) {
            // Update last login and return
            updateLastLogin(existing.getId());
            return existing;
        }
        
        // Check if email exists with local auth
        existing = findByEmail(email);
        if (existing != null) {
            // Link Google account to existing user
            String query = "UPDATE users SET google_id = ?, avatar_url = COALESCE(avatar_url, ?) WHERE id = ?::uuid";
            try {
                db.executeUpdate(query, googleId, avatarUrl, existing.getId());
                existing.setGoogleId(googleId);
                existing.setAvatarUrl(avatarUrl);
                updateLastLogin(existing.getId());
                logger.logInfo("Linked Google account to existing user: " + email);
                return existing;
            } catch (SQLException e) {
                logger.logError("Failed to link Google account: " + e.getMessage());
                return null;
            }
        }
        
        // Create new user with Google auth
        String username = generateUsernameFromEmail(email);
        String query = """
            INSERT INTO users (email, username, display_name, avatar_url, auth_provider, google_id, email_verified)
            VALUES (?, ?, ?, ?, 'google', ?, true)
            RETURNING id, created_at
            """;
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, email.toLowerCase());
                stmt.setString(2, username);
                stmt.setString(3, displayName);
                stmt.setString(4, avatarUrl);
                stmt.setString(5, googleId);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getString("id"));
                    user.setEmail(email.toLowerCase());
                    user.setUsername(username);
                    user.setDisplayName(displayName);
                    user.setAvatarUrl(avatarUrl);
                    user.setAuthProvider("google");
                    user.setGoogleId(googleId);
                    user.setEmailVerified(true);
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    
                    rs.close();
                    stmt.close();
                    logger.logInfo("Created new Google user: " + email);
                    return user;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to create Google user: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Finds a user by email.
     */
    public User findByEmail(String email) {
        String query = "SELECT * FROM users WHERE email = ?";
        return findOneByQuery(query, email.toLowerCase());
    }
    
    /**
     * Finds a user by username.
     */
    public User findByUsername(String username) {
        String query = "SELECT * FROM users WHERE username = ?";
        return findOneByQuery(query, username);
    }
    
    /**
     * Finds a user by ID.
     */
    public User findById(String id) {
        String query = "SELECT * FROM users WHERE id = ?::uuid";
        return findOneByQuery(query, id);
    }
    
    /**
     * Finds a user by Google ID.
     */
    public User findByGoogleId(String googleId) {
        String query = "SELECT * FROM users WHERE google_id = ?";
        return findOneByQuery(query, googleId);
    }
    
    /**
     * Validates user credentials for local auth.
     * 
     * @param emailOrUsername Email or username
     * @param passwordHash Hashed password
     * @return User if valid, null otherwise
     */
    public User validateCredentials(String emailOrUsername, String passwordHash) {
        String query = """
            SELECT * FROM users 
            WHERE (email = ? OR username = ?) 
            AND password_hash = ? 
            AND is_active = true
            """;
        
        User user = findOneByQuery(query, emailOrUsername.toLowerCase(), emailOrUsername, passwordHash);
        if (user != null) {
            updateLastLogin(user.getId());
        }
        return user;
    }
    
    /**
     * Updates user's last login timestamp.
     */
    public void updateLastLogin(String userId) {
        String query = "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?::uuid";
        try {
            db.executeUpdate(query, userId);
        } catch (SQLException e) {
            logger.logError("Failed to update last login: " + e.getMessage());
        }
    }
    
    /**
     * Updates user profile.
     */
    public boolean updateProfile(String userId, String displayName, String avatarUrl) {
        String query = "UPDATE users SET display_name = ?, avatar_url = ? WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, displayName, avatarUrl, userId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to update profile: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates user password.
     */
    public boolean updatePassword(String userId, String newPasswordHash) {
        String query = "UPDATE users SET password_hash = ? WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, newPasswordHash, userId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to update password: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets all users (for admin).
     */
    public List<User> getAllUsers(int limit, int offset) {
        String query = "SELECT * FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<User> users = new ArrayList<>();
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get users: " + e.getMessage());
        }
        return users;
    }
    
    /**
     * Gets total user count.
     */
    public int getUserCount() {
        String query = "SELECT COUNT(*) FROM users";
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    rs.close();
                    stmt.close();
                    return count;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get user count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Deactivates a user account.
     */
    public boolean deactivateUser(String userId) {
        String query = "UPDATE users SET is_active = false WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, userId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to deactivate user: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Activates a user account.
     */
    public boolean activateUser(String userId) {
        String query = "UPDATE users SET is_active = true WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, userId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to activate user: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sets user admin status.
     */
    public boolean setAdminStatus(String userId, boolean isAdmin) {
        String query = "UPDATE users SET is_admin = ? WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, isAdmin, userId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to set admin status: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if email is available.
     */
    public boolean isEmailAvailable(String email) {
        return findByEmail(email) == null;
    }
    
    /**
     * Checks if username is available.
     */
    public boolean isUsernameAvailable(String username) {
        return findByUsername(username) == null;
    }
    
    // Helper methods
    
    private User findOneByQuery(String query, Object... params) {
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                ResultSet rs = stmt.executeQuery();
                User user = null;
                if (rs.next()) {
                    user = mapResultSetToUser(rs);
                }
                rs.close();
                stmt.close();
                return user;
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Database query failed: " + e.getMessage());
            return null;
        }
    }
    
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setDisplayName(rs.getString("display_name"));
        user.setAvatarUrl(rs.getString("avatar_url"));
        user.setAuthProvider(rs.getString("auth_provider"));
        user.setGoogleId(rs.getString("google_id"));
        user.setActive(rs.getBoolean("is_active"));
        user.setAdmin(rs.getBoolean("is_admin"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setUpdatedAt(rs.getTimestamp("updated_at"));
        user.setLastLoginAt(rs.getTimestamp("last_login_at"));
        return user;
    }
    
    private String generateUsernameFromEmail(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        if (base.length() > 20) base = base.substring(0, 20);
        
        String username = base;
        int suffix = 1;
        while (!isUsernameAvailable(username)) {
            username = base + suffix++;
        }
        return username;
    }
}
