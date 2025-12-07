package auth;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import logging.ServerLogger;
import util.ConfigLoader;

/**
 * Google OAuth 2.0 client for authentication.
 * Handles authorization code exchange and user info retrieval.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class GoogleOAuthClient {
    
    private static GoogleOAuthClient instance;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ServerLogger logger;
    
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SCOPES = "openid email profile";
    
    /**
     * Private constructor for singleton.
     */
    private GoogleOAuthClient(ConfigLoader config) {
        this.logger = ServerLogger.getInstance();
        this.clientId = config.getGoogleClientId();
        this.clientSecret = config.getGoogleClientSecret();
        this.redirectUri = config.getGoogleRedirectUri();
        
        if (clientId == null || clientId.isEmpty()) {
            logger.logInfo("Google OAuth not configured - missing client ID");
        } else {
            logger.logInfo("Google OAuth configured with client ID: " + 
                clientId.substring(0, Math.min(10, clientId.length())) + "...");
        }
    }
    
    /**
     * Initializes the OAuth client.
     */
    public static synchronized void initialize(ConfigLoader config) {
        if (instance == null) {
            instance = new GoogleOAuthClient(config);
        }
    }
    
    /**
     * Gets the singleton instance.
     */
    public static synchronized GoogleOAuthClient getInstance() {
        return instance;
    }
    
    /**
     * Checks if Google OAuth is configured.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty() 
            && clientSecret != null && !clientSecret.isEmpty();
    }
    
    /**
     * Generates the authorization URL for initiating OAuth flow.
     * 
     * @param state Random state for CSRF protection
     * @return Authorization URL
     */
    public String getAuthorizationUrl(String state) {
        if (!isConfigured()) {
            return null;
        }
        
        try {
            StringBuilder url = new StringBuilder(AUTH_URL);
            url.append("?client_id=").append(URLEncoder.encode(clientId, "UTF-8"));
            url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"));
            url.append("&response_type=code");
            url.append("&scope=").append(URLEncoder.encode(SCOPES, "UTF-8"));
            url.append("&state=").append(URLEncoder.encode(state, "UTF-8"));
            url.append("&access_type=offline");
            url.append("&prompt=consent");
            return url.toString();
        } catch (UnsupportedEncodingException e) {
            logger.logError("Failed to create auth URL: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Exchanges authorization code for tokens.
     * 
     * @param code Authorization code from Google
     * @return Token response or null on failure
     */
    public TokenResponse exchangeCodeForTokens(String code) {
        if (!isConfigured()) {
            return null;
        }
        
        try {
            // Build request body
            StringBuilder body = new StringBuilder();
            body.append("code=").append(URLEncoder.encode(code, "UTF-8"));
            body.append("&client_id=").append(URLEncoder.encode(clientId, "UTF-8"));
            body.append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"));
            body.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"));
            body.append("&grant_type=authorization_code");
            
            // Make request
            URL url = new URL(TOKEN_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            String response = readResponse(conn);
            
            if (responseCode == 200) {
                return parseTokenResponse(response);
            } else {
                logger.logError("Token exchange failed: " + response);
                return null;
            }
            
        } catch (Exception e) {
            logger.logError("Token exchange error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets user info using access token.
     * 
     * @param accessToken Google access token
     * @return User info or null on failure
     */
    public GoogleUserInfo getUserInfo(String accessToken) {
        try {
            URL url = new URL(USERINFO_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            
            int responseCode = conn.getResponseCode();
            String response = readResponse(conn);
            
            if (responseCode == 200) {
                return parseUserInfo(response);
            } else {
                logger.logError("User info request failed: " + response);
                return null;
            }
            
        } catch (Exception e) {
            logger.logError("User info error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Generates a random state string for CSRF protection.
     */
    public String generateState() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    // Helper methods
    
    private String readResponse(HttpsURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() >= 400 
            ? conn.getErrorStream() 
            : conn.getInputStream();
        
        if (is == null) return "";
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
    
    private TokenResponse parseTokenResponse(String json) {
        TokenResponse response = new TokenResponse();
        
        // Simple JSON parsing
        response.setAccessToken(extractJsonValue(json, "access_token"));
        response.setRefreshToken(extractJsonValue(json, "refresh_token"));
        response.setIdToken(extractJsonValue(json, "id_token"));
        response.setTokenType(extractJsonValue(json, "token_type"));
        
        String expiresIn = extractJsonValue(json, "expires_in");
        if (expiresIn != null) {
            response.setExpiresIn(Integer.parseInt(expiresIn));
        }
        
        return response;
    }
    
    private GoogleUserInfo parseUserInfo(String json) {
        GoogleUserInfo info = new GoogleUserInfo();
        info.setSub(extractJsonValue(json, "sub"));
        info.setEmail(extractJsonValue(json, "email"));
        info.setEmailVerified("true".equals(extractJsonValue(json, "email_verified")));
        info.setName(extractJsonValue(json, "name"));
        info.setGivenName(extractJsonValue(json, "given_name"));
        info.setFamilyName(extractJsonValue(json, "family_name"));
        info.setPicture(extractJsonValue(json, "picture"));
        return info;
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        if (json.charAt(valueStart) == '"') {
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else {
            int valueEnd = valueStart;
            while (valueEnd < json.length() && 
                   !Character.isWhitespace(json.charAt(valueEnd)) &&
                   json.charAt(valueEnd) != ',' &&
                   json.charAt(valueEnd) != '}') {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }
    
    /**
     * Token response from Google.
     */
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String idToken;
        private String tokenType;
        private int expiresIn;
        
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        
        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }
        
        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }
        
        public int getExpiresIn() { return expiresIn; }
        public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }
    }
    
    /**
     * User info from Google.
     */
    public static class GoogleUserInfo {
        private String sub;
        private String email;
        private boolean emailVerified;
        private String name;
        private String givenName;
        private String familyName;
        private String picture;
        
        public String getSub() { return sub; }
        public void setSub(String sub) { this.sub = sub; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public boolean isEmailVerified() { return emailVerified; }
        public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getGivenName() { return givenName; }
        public void setGivenName(String givenName) { this.givenName = givenName; }
        
        public String getFamilyName() { return familyName; }
        public void setFamilyName(String familyName) { this.familyName = familyName; }
        
        public String getPicture() { return picture; }
        public void setPicture(String picture) { this.picture = picture; }
    }
}
