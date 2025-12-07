package admin;

import java.io.*;
import java.nio.file.*;
import java.util.Map;

import server.*;
import chat.ChatSessionManager;
import logging.ServerLogger;

/**
 * Handles HTTP requests for the /admin endpoints.
 * 
 * Endpoints:
 * - GET /admin/login - Login page
 * - POST /admin/login - Process login
 * - GET /admin/dashboard - Admin dashboard (requires auth)
 * - GET /admin/logout - Logout
 * - GET /admin/api/status - Server status JSON
 * - GET /admin/api/sessions - Chat sessions JSON
 * - GET /admin/api/logs - Server logs
 * - POST /admin/api/model - Change ChatGPT model
 * - POST /admin/api/toggle-chat - Enable/disable chat
 * 
 * All routes except /login require authentication.
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class AdminHandler {
    
    /** Reference to the web server */
    private final WebServer server;
    
    /** Authentication manager */
    private final AuthManager authManager;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /** Static files directory */
    private static final String STATIC_DIR = "public";
    
    /**
     * Constructs a new AdminHandler.
     * 
     * @param server Reference to the WebServer instance
     */
    public AdminHandler(WebServer server) {
        this.server = server;
        this.authManager = AuthManager.getInstance();
        this.logger = ServerLogger.getInstance();
        
        // Configure credentials from config
        String username = server.getConfig().getAdminUsername();
        String passwordHash = server.getConfig().getAdminPasswordHash();
        authManager.setCredentials(username, passwordHash);
        
        // Configure 2FA if enabled
        if (server.getConfig().is2FAEnabled()) {
            authManager.setTwoFactorAuth(true, server.getConfig().get2FACode());
        }
    }
    
    /**
     * Handles an admin-related HTTP request.
     * 
     * @param request The HTTP request
     * @return The HTTP response
     */
    public HttpResponse handle(HttpRequest request) {
        String method = request.getMethod();
        String path = request.getPath();
        
        // Route based on path
        switch (path) {
            case "/admin":
            case "/admin/":
                // Redirect to dashboard or login
                if (isAuthenticated(request)) {
                    return HttpResponse.redirect("/admin/dashboard");
                } else {
                    return HttpResponse.redirect("/admin/login");
                }
                
            case "/admin/login":
                if (method.equals("GET")) {
                    return handleLoginPage(request);
                } else if (method.equals("POST")) {
                    return handleLogin(request);
                }
                return HttpResponse.methodNotAllowed("GET, POST");
                
            case "/admin/logout":
                return handleLogout(request);
                
            case "/admin/dashboard":
                return handleDashboard(request);
                
            case "/admin/api/status":
                return handleApiStatus(request);
                
            case "/admin/api/sessions":
                return handleApiSessions(request);
                
            case "/admin/api/logs":
                return handleApiLogs(request);
                
            case "/admin/api/model":
                return handleApiModel(request);
                
            case "/admin/api/toggle-chat":
                return handleApiToggleChat(request);
                
            default:
                return HttpResponse.notFound("Admin endpoint not found");
        }
    }
    
    /**
     * Checks if the request is authenticated.
     * 
     * @param request The HTTP request
     * @return true if authenticated
     */
    private boolean isAuthenticated(HttpRequest request) {
        String sessionToken = request.getCookie("admin_session");
        return authManager.validateSession(sessionToken);
    }
    
    /**
     * Gets the session token from request.
     * 
     * @param request The HTTP request
     * @return Session token or null
     */
    private String getSessionToken(HttpRequest request) {
        return request.getCookie("admin_session");
    }
    
    /**
     * Returns unauthorized response if not authenticated.
     * 
     * @param request The HTTP request
     * @return HttpResponse if not authenticated, null if authenticated
     */
    private HttpResponse requireAuth(HttpRequest request) {
        if (!isAuthenticated(request)) {
            return HttpResponse.redirect("/admin/login");
        }
        return null;
    }
    
    /**
     * Handles GET /admin/login - returns login page.
     */
    private HttpResponse handleLoginPage(HttpRequest request) {
        // If already logged in, redirect to dashboard
        if (isAuthenticated(request)) {
            return HttpResponse.redirect("/admin/dashboard");
        }
        
        return serveStaticFile("login.html");
    }
    
    /**
     * Handles POST /admin/login - processes login.
     */
    private HttpResponse handleLogin(HttpRequest request) {
        // Parse form data
        Map<String, String> formData = RequestParser.parseFormData(request.getBody());
        String username = formData.get("username");
        String password = formData.get("password");
        String otpCode = formData.get("otp");
        
        if (username == null || password == null) {
            return loginError("Username and password required");
        }
        
        // Get client IP (from headers or connection)
        String clientIP = request.getHeader("x-forwarded-for");
        if (clientIP == null) {
            clientIP = "unknown";
        }
        
        // Attempt authentication
        String sessionToken = authManager.authenticate(username, password, clientIP);
        
        if (sessionToken == null) {
            logger.logError("Failed login attempt for user: " + username);
            return loginError("Invalid credentials");
        }
        
        // Check if 2FA is required
        if (sessionToken.startsWith("2FA_PENDING:")) {
            if (authManager.isTwoFactorEnabled()) {
                if (otpCode == null || otpCode.isEmpty()) {
                    // Return 2FA form
                    return login2FARequired(sessionToken);
                }
                
                // Validate 2FA
                sessionToken = authManager.validate2FA(sessionToken, otpCode, clientIP);
                if (sessionToken == null) {
                    return loginError("Invalid 2FA code");
                }
            }
        }
        
        // Success - set cookie and redirect
        HttpResponse response = HttpResponse.redirect("/admin/dashboard");
        response.setCookie("admin_session", sessionToken, true);
        
        logger.logInfo("Admin login successful: " + username);
        
        return response;
    }
    
    /**
     * Returns login page with error message.
     */
    private HttpResponse loginError(String message) {
        String html = getLoginPageHtml(message, false);
        return HttpResponse.html(html);
    }
    
    /**
     * Returns 2FA input form.
     */
    private HttpResponse login2FARequired(String pendingToken) {
        String html = getLoginPageHtml(null, true);
        HttpResponse response = HttpResponse.html(html);
        // Store pending token in cookie temporarily
        response.setCookie("2fa_pending", pendingToken, 300, true);
        return response;
    }
    
    /**
     * Generates login page HTML.
     */
    private String getLoginPageHtml(String error, boolean show2FA) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Admin Login - ChatGPT Server</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"/styles.css\">\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"login-container\">\n");
        html.append("        <h1>Admin Login</h1>\n");
        
        if (error != null) {
            html.append("        <div class=\"error\">").append(escapeHtml(error)).append("</div>\n");
        }
        
        html.append("        <form method=\"POST\" action=\"/admin/login\">\n");
        html.append("            <div class=\"form-group\">\n");
        html.append("                <label for=\"username\">Username</label>\n");
        html.append("                <input type=\"text\" id=\"username\" name=\"username\" required>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"form-group\">\n");
        html.append("                <label for=\"password\">Password</label>\n");
        html.append("                <input type=\"password\" id=\"password\" name=\"password\" required>\n");
        html.append("            </div>\n");
        
        if (show2FA) {
            html.append("            <div class=\"form-group\">\n");
            html.append("                <label for=\"otp\">2FA Code</label>\n");
            html.append("                <input type=\"text\" id=\"otp\" name=\"otp\" pattern=\"[0-9]{6}\" required>\n");
            html.append("            </div>\n");
        }
        
        html.append("            <button type=\"submit\">Login</button>\n");
        html.append("        </form>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * Handles /admin/logout.
     */
    private HttpResponse handleLogout(HttpRequest request) {
        String sessionToken = getSessionToken(request);
        
        if (sessionToken != null) {
            authManager.invalidateSession(sessionToken);
        }
        
        HttpResponse response = HttpResponse.redirect("/admin/login");
        response.clearCookie("admin_session");
        
        return response;
    }
    
    /**
     * Handles GET /admin/dashboard.
     */
    private HttpResponse handleDashboard(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) return authCheck;
        
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        return serveStaticFile("admin.html");
    }
    
    /**
     * Handles GET /admin/api/status.
     */
    private HttpResponse handleApiStatus(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) {
            return HttpResponse.unauthorized("Authentication required");
        }
        
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        // Build status JSON
        long uptime = server.getUptime();
        long totalRequests = server.getTotalRequests();
        int totalSessions = ChatSessionManager.getInstance().getSessionCount();
        String currentModel = server.getCurrentModel();
        int activeConnections = server.getActiveConnections();
        boolean chatEnabled = server.isChatEnabled();
        
        String json = String.format(
            "{\"uptime\":%d,\"uptimeFormatted\":\"%s\",\"totalRequests\":%d," +
            "\"totalChatSessions\":%d,\"currentModel\":\"%s\"," +
            "\"activeConnections\":%d,\"chatEnabled\":%b}",
            uptime,
            formatUptime(uptime),
            totalRequests,
            totalSessions,
            escapeJson(currentModel),
            activeConnections,
            chatEnabled
        );
        
        return HttpResponse.json(json);
    }
    
    /**
     * Handles GET /admin/api/sessions.
     */
    private HttpResponse handleApiSessions(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) {
            return HttpResponse.unauthorized("Authentication required");
        }
        
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        // Get limit from query params
        String limitStr = request.getQueryParam("limit");
        int limit = 100;
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        
        String json = ChatSessionManager.getInstance().toJson(limit);
        return HttpResponse.json(json);
    }
    
    /**
     * Handles GET /admin/api/logs.
     */
    private HttpResponse handleApiLogs(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) {
            return HttpResponse.unauthorized("Authentication required");
        }
        
        if (!request.getMethod().equals("GET") && !request.getMethod().equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        // Get lines from query params
        String linesStr = request.getQueryParam("lines");
        int lines = 100;
        if (linesStr != null) {
            try {
                lines = Integer.parseInt(linesStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        
        String logs = logger.getRecentLogs(lines);
        
        // Return as JSON with escaped log content
        String json = "{\"logs\":\"" + escapeJson(logs) + "\"}";
        return HttpResponse.json(json);
    }
    
    /**
     * Handles POST /admin/api/model.
     */
    private HttpResponse handleApiModel(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) {
            return HttpResponse.unauthorized("Authentication required");
        }
        
        if (!request.getMethod().equals("POST")) {
            return HttpResponse.methodNotAllowed("POST");
        }
        
        // Parse request body
        String body = request.getBody();
        String model = RequestParser.parseJsonValue(body, "model");
        
        if (model == null || model.isEmpty()) {
            Map<String, String> formData = RequestParser.parseFormData(body);
            model = formData.get("model");
        }
        
        if (model == null || model.isEmpty()) {
            return HttpResponse.badRequest("Model parameter required");
        }
        
        // Update model
        server.setCurrentModel(model);
        logger.logInfo("Admin changed model to: " + model);
        
        String json = "{\"success\":true,\"model\":\"" + escapeJson(model) + "\"}";
        return HttpResponse.json(json);
    }
    
    /**
     * Handles POST /admin/api/toggle-chat.
     */
    private HttpResponse handleApiToggleChat(HttpRequest request) {
        HttpResponse authCheck = requireAuth(request);
        if (authCheck != null) {
            return HttpResponse.unauthorized("Authentication required");
        }
        
        if (!request.getMethod().equals("POST")) {
            return HttpResponse.methodNotAllowed("POST");
        }
        
        // Parse request body
        String body = request.getBody();
        String enabledStr = RequestParser.parseJsonValue(body, "enabled");
        
        if (enabledStr == null) {
            Map<String, String> formData = RequestParser.parseFormData(body);
            enabledStr = formData.get("enabled");
        }
        
        boolean enabled;
        if (enabledStr == null) {
            // Toggle current state
            enabled = !server.isChatEnabled();
        } else {
            enabled = enabledStr.equalsIgnoreCase("true") || enabledStr.equals("1");
        }
        
        // Update state
        server.setChatEnabled(enabled);
        logger.logInfo("Admin " + (enabled ? "enabled" : "disabled") + " chat");
        
        String json = "{\"success\":true,\"chatEnabled\":" + enabled + "}";
        return HttpResponse.json(json);
    }
    
    /**
     * Serves a static file from the public directory.
     */
    private HttpResponse serveStaticFile(String filename) {
        try {
            Path filePath = Paths.get(STATIC_DIR, filename);
            if (Files.exists(filePath)) {
                String content = new String(Files.readAllBytes(filePath));
                return HttpResponse.html(content);
            }
        } catch (IOException e) {
            logger.logError("Error reading file: " + filename);
        }
        
        return HttpResponse.notFound("File not found: " + filename);
    }
    
    /**
     * Formats uptime in human-readable format.
     */
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    /**
     * Escapes JSON special characters.
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
