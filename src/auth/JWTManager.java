package auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import logging.ServerLogger;
import util.ConfigLoader;

/**
 * JWT (JSON Web Token) Manager for user authentication.
 * Implements simple JWT creation and validation without external libraries.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class JWTManager {
    
    private static JWTManager instance;
    private final String secretKey;
    private final long accessTokenExpiry;  // milliseconds
    private final long refreshTokenExpiry; // milliseconds
    private final ServerLogger logger;
    
    private static final String ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_ACCESS_EXPIRY = 15 * 60 * 1000;      // 15 minutes
    private static final long DEFAULT_REFRESH_EXPIRY = 7 * 24 * 60 * 60 * 1000; // 7 days
    
    /**
     * Private constructor for singleton.
     */
    private JWTManager(ConfigLoader config) {
        this.logger = ServerLogger.getInstance();
        
        // Get or generate secret key
        String configKey = config.getJwtSecret();
        if (configKey == null || configKey.isEmpty()) {
            configKey = generateSecretKey();
            logger.logInfo("Generated new JWT secret key");
        }
        this.secretKey = configKey;
        
        this.accessTokenExpiry = config.getJwtAccessExpiry(DEFAULT_ACCESS_EXPIRY);
        this.refreshTokenExpiry = config.getJwtRefreshExpiry(DEFAULT_REFRESH_EXPIRY);
    }
    
    /**
     * Initializes the JWT manager.
     */
    public static synchronized void initialize(ConfigLoader config) {
        if (instance == null) {
            instance = new JWTManager(config);
        }
    }
    
    /**
     * Gets the singleton instance.
     */
    public static synchronized JWTManager getInstance() {
        return instance;
    }
    
    /**
     * Generates an access token for a user.
     * 
     * @param userId User ID
     * @param username Username
     * @param isAdmin Whether user is admin
     * @return JWT access token
     */
    public String generateAccessToken(String userId, String username, boolean isAdmin) {
        long now = System.currentTimeMillis();
        long exp = now + accessTokenExpiry;
        
        // Build payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("admin", isAdmin);
        payload.put("iat", now / 1000);
        payload.put("exp", exp / 1000);
        payload.put("type", "access");
        
        return createToken(payload);
    }
    
    /**
     * Generates a refresh token for a user.
     * 
     * @param userId User ID
     * @return JWT refresh token
     */
    public String generateRefreshToken(String userId) {
        long now = System.currentTimeMillis();
        long exp = now + refreshTokenExpiry;
        
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("iat", now / 1000);
        payload.put("exp", exp / 1000);
        payload.put("type", "refresh");
        payload.put("jti", UUID.randomUUID().toString()); // Unique token ID
        
        return createToken(payload);
    }
    
    /**
     * Validates a token and returns the payload.
     * 
     * @param token JWT token
     * @return Token payload or null if invalid
     */
    public TokenPayload validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];
            
            // Verify signature
            String expectedSignature = sign(header + "." + payload);
            if (!signature.equals(expectedSignature)) {
                logger.logError("Invalid token signature");
                return null;
            }
            
            // Decode payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            TokenPayload tokenPayload = parsePayload(payloadJson);
            
            // Check expiration
            if (tokenPayload.getExp() * 1000 < System.currentTimeMillis()) {
                logger.logError("Token expired");
                return null;
            }
            
            return tokenPayload;
            
        } catch (Exception e) {
            logger.logError("Token validation failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validates an access token specifically.
     */
    public TokenPayload validateAccessToken(String token) {
        TokenPayload payload = validateToken(token);
        if (payload != null && "access".equals(payload.getType())) {
            return payload;
        }
        return null;
    }
    
    /**
     * Validates a refresh token specifically.
     */
    public TokenPayload validateRefreshToken(String token) {
        TokenPayload payload = validateToken(token);
        if (payload != null && "refresh".equals(payload.getType())) {
            return payload;
        }
        return null;
    }
    
    /**
     * Extracts token from Authorization header.
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * Gets remaining token validity in seconds.
     */
    public long getTokenRemainingSeconds(String token) {
        TokenPayload payload = validateToken(token);
        if (payload != null) {
            return payload.getExp() - (System.currentTimeMillis() / 1000);
        }
        return 0;
    }
    
    // Private helper methods
    
    private String createToken(Map<String, Object> payload) {
        // Header
        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        
        // Payload
        String payloadStr = base64UrlEncode(mapToJson(payload));
        
        // Signature
        String signature = sign(header + "." + payloadStr);
        
        return header + "." + payloadStr + "." + signature;
    }
    
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }
    
    private String base64UrlEncode(String str) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }
    
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else if (value instanceof Boolean || value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
    
    private TokenPayload parsePayload(String json) {
        TokenPayload payload = new TokenPayload();
        
        // Simple JSON parsing without external library
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                
                switch (key) {
                    case "sub" -> payload.setUserId(value);
                    case "username" -> payload.setUsername(value);
                    case "admin" -> payload.setAdmin("true".equals(value));
                    case "iat" -> payload.setIat(Long.parseLong(value));
                    case "exp" -> payload.setExp(Long.parseLong(value));
                    case "type" -> payload.setType(value);
                    case "jti" -> payload.setJti(value);
                }
            }
        }
        
        return payload;
    }
    
    private String generateSecretKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
    
    /**
     * Token payload data class.
     */
    public static class TokenPayload {
        private String userId;
        private String username;
        private boolean admin;
        private long iat;
        private long exp;
        private String type;
        private String jti;
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public boolean isAdmin() { return admin; }
        public void setAdmin(boolean admin) { this.admin = admin; }
        
        public long getIat() { return iat; }
        public void setIat(long iat) { this.iat = iat; }
        
        public long getExp() { return exp; }
        public void setExp(long exp) { this.exp = exp; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getJti() { return jti; }
        public void setJti(String jti) { this.jti = jti; }
    }
}
