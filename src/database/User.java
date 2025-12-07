package database;

import java.sql.Timestamp;

/**
 * User entity class.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class User {
    
    private String id;
    private String email;
    private String username;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private String authProvider;
    private String googleId;
    private boolean isActive;
    private boolean isAdmin;
    private boolean emailVerified;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastLoginAt;
    private String settings;
    
    // Getters and Setters
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    
    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
    
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    public Timestamp getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Timestamp lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    
    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
    
    /**
     * Checks if user can login with local authentication.
     */
    public boolean hasLocalAuth() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
    
    /**
     * Checks if user can login with Google.
     */
    public boolean hasGoogleAuth() {
        return googleId != null && !googleId.isEmpty();
    }
    
    /**
     * Converts to JSON for API responses (excludes sensitive data).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"email\":\"").append(escapeJson(email)).append("\",");
        sb.append("\"username\":\"").append(escapeJson(username)).append("\",");
        sb.append("\"displayName\":\"").append(escapeJson(displayName)).append("\",");
        sb.append("\"avatarUrl\":").append(avatarUrl != null ? "\"" + escapeJson(avatarUrl) + "\"" : "null").append(",");
        sb.append("\"authProvider\":\"").append(escapeJson(authProvider)).append("\",");
        sb.append("\"isActive\":").append(isActive).append(",");
        sb.append("\"isAdmin\":").append(isAdmin).append(",");
        sb.append("\"emailVerified\":").append(emailVerified).append(",");
        sb.append("\"createdAt\":\"").append(createdAt != null ? createdAt.toString() : "").append("\",");
        sb.append("\"lastLoginAt\":").append(lastLoginAt != null ? "\"" + lastLoginAt.toString() + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Converts to safe JSON for client (minimal data).
     */
    public String toPublicJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"username\":\"").append(escapeJson(username)).append("\",");
        sb.append("\"displayName\":\"").append(escapeJson(displayName)).append("\",");
        sb.append("\"avatarUrl\":").append(avatarUrl != null ? "\"" + escapeJson(avatarUrl) + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
