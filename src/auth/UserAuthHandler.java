package auth;

import java.util.*;
import server.*;
import database.*;
import logging.ServerLogger;
import util.SecurityUtils;

/**
 * Handles user authentication endpoints.
 * Supports local signup/login and Google OAuth.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class UserAuthHandler {
    
    private final UserDAO userDAO;
    private final JWTManager jwtManager;
    private final GoogleOAuthClient googleOAuth;
    private final ServerLogger logger;
    
    // OAuth state storage (should use Redis in production)
    private final Map<String, Long> oauthStates = new HashMap<>();
    private static final long STATE_EXPIRY = 10 * 60 * 1000; // 10 minutes
    
    public UserAuthHandler() {
        this.userDAO = new UserDAO();
        this.jwtManager = JWTManager.getInstance();
        this.googleOAuth = GoogleOAuthClient.getInstance();
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Handles authentication-related requests.
     */
    public HttpResponse handle(HttpRequest request) {
        String path = request.getPath();
        String method = request.getMethod();
        
        return switch (path) {
            case "/auth/signup" -> method.equals("POST") ? handleSignup(request) : HttpResponse.methodNotAllowed();
            case "/auth/login" -> method.equals("POST") ? handleLogin(request) : HttpResponse.methodNotAllowed();
            case "/auth/logout" -> method.equals("POST") ? handleLogout(request) : HttpResponse.methodNotAllowed();
            case "/auth/refresh" -> method.equals("POST") ? handleRefreshToken(request) : HttpResponse.methodNotAllowed();
            case "/auth/me" -> method.equals("GET") ? handleGetCurrentUser(request) : HttpResponse.methodNotAllowed();
            case "/auth/google" -> method.equals("GET") ? handleGoogleAuthStart(request) : HttpResponse.methodNotAllowed();
            case "/auth/google/callback" -> method.equals("GET") ? handleGoogleCallback(request) : HttpResponse.methodNotAllowed();
            case "/auth/check-email" -> method.equals("GET") ? handleCheckEmail(request) : HttpResponse.methodNotAllowed();
            case "/auth/check-username" -> method.equals("GET") ? handleCheckUsername(request) : HttpResponse.methodNotAllowed();
            default -> HttpResponse.notFound("Auth endpoint not found");
        };
    }
    
    /**
     * Handles user signup.
     */
    private HttpResponse handleSignup(HttpRequest request) {
        try {
            String body = request.getBody();
            String email = extractJsonValue(body, "email");
            String username = extractJsonValue(body, "username");
            String password = extractJsonValue(body, "password");
            String displayName = extractJsonValue(body, "displayName");
            
            // Validation
            if (email == null || email.isEmpty()) {
                return HttpResponse.badRequest("{\"error\":\"Email is required\"}");
            }
            if (username == null || username.isEmpty()) {
                return HttpResponse.badRequest("{\"error\":\"Username is required\"}");
            }
            if (password == null || password.length() < 8) {
                return HttpResponse.badRequest("{\"error\":\"Password must be at least 8 characters\"}");
            }
            if (!isValidEmail(email)) {
                return HttpResponse.badRequest("{\"error\":\"Invalid email format\"}");
            }
            if (!isValidUsername(username)) {
                return HttpResponse.badRequest("{\"error\":\"Username can only contain letters, numbers, and underscores\"}");
            }
            
            // Check availability
            if (!userDAO.isEmailAvailable(email)) {
                return HttpResponse.badRequest("{\"error\":\"Email already in use\"}");
            }
            if (!userDAO.isUsernameAvailable(username)) {
                return HttpResponse.badRequest("{\"error\":\"Username already taken\"}");
            }
            
            // Create user
            String passwordHash = SecurityUtils.sha256(password);
            User user = userDAO.createUser(email, username, passwordHash, displayName);
            
            if (user == null) {
                return HttpResponse.serverError("{\"error\":\"Failed to create account\"}");
            }
            
            // Generate tokens
            String accessToken = jwtManager.generateAccessToken(user.getId(), user.getUsername(), user.isAdmin());
            String refreshToken = jwtManager.generateRefreshToken(user.getId());
            
            logger.logInfo("New user registered: " + username);
            
            // Build response
            String responseJson = String.format(
                "{\"user\":%s,\"accessToken\":\"%s\",\"refreshToken\":\"%s\"}",
                user.toPublicJson(), accessToken, refreshToken
            );
            
            HttpResponse response = HttpResponse.createdJson(responseJson);
            return response;
            
        } catch (Exception e) {
            logger.logError("Signup error: " + e.getMessage());
            return HttpResponse.serverError("{\"error\":\"Registration failed\"}");
        }
    }
    
    /**
     * Handles user login.
     */
    private HttpResponse handleLogin(HttpRequest request) {
        try {
            String body = request.getBody();
            String emailOrUsername = extractJsonValue(body, "email");
            if (emailOrUsername == null) {
                emailOrUsername = extractJsonValue(body, "username");
            }
            String password = extractJsonValue(body, "password");
            
            if (emailOrUsername == null || password == null) {
                return HttpResponse.badRequest("{\"error\":\"Email/username and password are required\"}");
            }
            
            String passwordHash = SecurityUtils.sha256(password);
            User user = userDAO.validateCredentials(emailOrUsername, passwordHash);
            
            if (user == null) {
                logger.logError("Failed login attempt for: " + emailOrUsername);
                return HttpResponse.unauthorized("{\"error\":\"Invalid credentials\"}");
            }
            
            if (!user.isActive()) {
                return HttpResponse.forbidden("{\"error\":\"Account is deactivated\"}");
            }
            
            // Generate tokens
            String accessToken = jwtManager.generateAccessToken(user.getId(), user.getUsername(), user.isAdmin());
            String refreshToken = jwtManager.generateRefreshToken(user.getId());
            
            logger.logInfo("User logged in: " + user.getUsername());
            
            String responseJson = String.format(
                "{\"user\":%s,\"accessToken\":\"%s\",\"refreshToken\":\"%s\"}",
                user.toPublicJson(), accessToken, refreshToken
            );
            
            HttpResponse response = HttpResponse.ok(responseJson);
            response.setHeader("Content-Type", "application/json");
            return response;
            
        } catch (Exception e) {
            logger.logError("Login error: " + e.getMessage());
            return HttpResponse.serverError("{\"error\":\"Login failed\"}");
        }
    }
    
    /**
     * Handles user logout.
     */
    private HttpResponse handleLogout(HttpRequest request) {
        // In a production app, you'd invalidate the refresh token in the database
        logger.logInfo("User logged out");
        return HttpResponse.ok("{\"message\":\"Logged out successfully\"}");
    }
    
    /**
     * Handles token refresh.
     */
    private HttpResponse handleRefreshToken(HttpRequest request) {
        try {
            String body = request.getBody();
            String refreshToken = extractJsonValue(body, "refreshToken");
            
            if (refreshToken == null) {
                return HttpResponse.badRequest("{\"error\":\"Refresh token required\"}");
            }
            
            JWTManager.TokenPayload payload = jwtManager.validateRefreshToken(refreshToken);
            if (payload == null) {
                return HttpResponse.unauthorized("{\"error\":\"Invalid or expired refresh token\"}");
            }
            
            // Get user
            User user = userDAO.findById(payload.getUserId());
            if (user == null || !user.isActive()) {
                return HttpResponse.unauthorized("{\"error\":\"User not found or inactive\"}");
            }
            
            // Generate new access token
            String newAccessToken = jwtManager.generateAccessToken(user.getId(), user.getUsername(), user.isAdmin());
            
            String responseJson = String.format("{\"accessToken\":\"%s\"}", newAccessToken);
            HttpResponse response = HttpResponse.ok(responseJson);
            response.setHeader("Content-Type", "application/json");
            return response;
            
        } catch (Exception e) {
            logger.logError("Token refresh error: " + e.getMessage());
            return HttpResponse.serverError("{\"error\":\"Token refresh failed\"}");
        }
    }
    
    /**
     * Gets current user info from token.
     */
    private HttpResponse handleGetCurrentUser(HttpRequest request) {
        User user = getUserFromRequest(request);
        if (user == null) {
            return HttpResponse.unauthorized("{\"error\":\"Not authenticated\"}");
        }
        
        HttpResponse response = HttpResponse.ok(user.toJson());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Starts Google OAuth flow.
     */
    private HttpResponse handleGoogleAuthStart(HttpRequest request) {
        if (!googleOAuth.isConfigured()) {
            return HttpResponse.badRequest("{\"error\":\"Google OAuth not configured\"}");
        }
        
        // Generate and store state
        String state = googleOAuth.generateState();
        oauthStates.put(state, System.currentTimeMillis());
        cleanupExpiredStates();
        
        String authUrl = googleOAuth.getAuthorizationUrl(state);
        
        // Redirect to Google
        HttpResponse response = new HttpResponse();
        response.setStatusCode(302);
        response.setStatusMessage("Found");
        response.setHeader("Location", authUrl);
        return response;
    }
    
    /**
     * Handles Google OAuth callback.
     */
    private HttpResponse handleGoogleCallback(HttpRequest request) {
        try {
            String code = request.getQueryParam("code");
            String state = request.getQueryParam("state");
            String error = request.getQueryParam("error");
            
            if (error != null) {
                return redirectWithError("Google authentication cancelled");
            }
            
            // Verify state
            Long stateTime = oauthStates.remove(state);
            if (stateTime == null || System.currentTimeMillis() - stateTime > STATE_EXPIRY) {
                return redirectWithError("Invalid or expired state");
            }
            
            // Exchange code for tokens
            GoogleOAuthClient.TokenResponse tokens = googleOAuth.exchangeCodeForTokens(code);
            if (tokens == null) {
                return redirectWithError("Failed to exchange authorization code");
            }
            
            // Get user info
            GoogleOAuthClient.GoogleUserInfo userInfo = googleOAuth.getUserInfo(tokens.getAccessToken());
            if (userInfo == null) {
                return redirectWithError("Failed to get user info");
            }
            
            // Create or update user
            User user = userDAO.createOrUpdateGoogleUser(
                userInfo.getSub(),
                userInfo.getEmail(),
                userInfo.getName(),
                userInfo.getPicture()
            );
            
            if (user == null) {
                return redirectWithError("Failed to create user account");
            }
            
            // Generate our tokens
            String accessToken = jwtManager.generateAccessToken(user.getId(), user.getUsername(), user.isAdmin());
            String refreshToken = jwtManager.generateRefreshToken(user.getId());
            
            logger.logInfo("Google OAuth login: " + user.getEmail());
            
            // Redirect to app with tokens
            String redirectUrl = String.format(
                "/?accessToken=%s&refreshToken=%s",
                accessToken, refreshToken
            );
            
            HttpResponse response = new HttpResponse();
            response.setStatusCode(302);
            response.setStatusMessage("Found");
            response.setHeader("Location", redirectUrl);
            return response;
            
        } catch (Exception e) {
            logger.logError("Google OAuth callback error: " + e.getMessage());
            return redirectWithError("Authentication failed");
        }
    }
    
    /**
     * Checks if email is available.
     */
    private HttpResponse handleCheckEmail(HttpRequest request) {
        String email = request.getQueryParam("email");
        if (email == null) {
            return HttpResponse.badRequest("{\"error\":\"Email required\"}");
        }
        
        boolean available = userDAO.isEmailAvailable(email);
        HttpResponse response = HttpResponse.ok("{\"available\":" + available + "}");
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Checks if username is available.
     */
    private HttpResponse handleCheckUsername(HttpRequest request) {
        String username = request.getQueryParam("username");
        if (username == null) {
            return HttpResponse.badRequest("{\"error\":\"Username required\"}");
        }
        
        boolean available = userDAO.isUsernameAvailable(username);
        HttpResponse response = HttpResponse.ok("{\"available\":" + available + "}");
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Gets user from request authorization header.
     */
    public User getUserFromRequest(HttpRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = jwtManager.extractTokenFromHeader(authHeader);
        
        if (token == null) {
            // Also check cookie
            String cookieToken = request.getCookie("accessToken");
            if (cookieToken != null) {
                token = cookieToken;
            }
        }
        
        if (token == null) return null;
        
        JWTManager.TokenPayload payload = jwtManager.validateAccessToken(token);
        if (payload == null) return null;
        
        return userDAO.findById(payload.getUserId());
    }
    
    // Helper methods
    
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
    
    private boolean isValidUsername(String username) {
        return username != null && username.matches("^[A-Za-z0-9_]{3,30}$");
    }
    
    private HttpResponse redirectWithError(String error) {
        try {
            String redirectUrl = "/login.html?error=" + java.net.URLEncoder.encode(error, "UTF-8");
            HttpResponse response = new HttpResponse();
            response.setStatusCode(302);
            response.setStatusMessage("Found");
            response.setHeader("Location", redirectUrl);
            return response;
        } catch (Exception e) {
            return HttpResponse.serverError("Redirect failed");
        }
    }
    
    private void cleanupExpiredStates() {
        long now = System.currentTimeMillis();
        oauthStates.entrySet().removeIf(entry -> now - entry.getValue() > STATE_EXPIRY);
    }
    
    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
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
        } else if (json.charAt(valueStart) == 'n') {
            return null; // null value
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
}
