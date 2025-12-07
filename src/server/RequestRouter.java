package server;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import admin.AdminHandler;
import chat.ChatHandler;
import logging.ServerLogger;
import util.SecurityUtils;

/**
 * Routes HTTP requests to appropriate handlers.
 * 
 * This class is responsible for:
 * - Matching request paths to handlers
 * - Serving static files
 * - Delegating to ChatHandler for /chat routes
 * - Delegating to AdminHandler for /admin routes
 * - Handling MIME types for static files
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class RequestRouter {
    
    /** Reference to the web server */
    private final WebServer server;
    
    /** Chat handler for /chat routes */
    private final ChatHandler chatHandler;
    
    /** Admin handler for /admin routes */
    private final AdminHandler adminHandler;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /** Static file root directory */
    private final String staticRoot;
    
    /** MIME type mappings */
    private static final Map<String, String> MIME_TYPES = new HashMap<>();
    
    static {
        // Text types
        MIME_TYPES.put(".html", "text/html; charset=UTF-8");
        MIME_TYPES.put(".htm", "text/html; charset=UTF-8");
        MIME_TYPES.put(".css", "text/css; charset=UTF-8");
        MIME_TYPES.put(".js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put(".json", "application/json; charset=UTF-8");
        MIME_TYPES.put(".xml", "application/xml; charset=UTF-8");
        MIME_TYPES.put(".txt", "text/plain; charset=UTF-8");
        
        // Image types
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".gif", "image/gif");
        MIME_TYPES.put(".svg", "image/svg+xml");
        MIME_TYPES.put(".ico", "image/x-icon");
        MIME_TYPES.put(".webp", "image/webp");
        
        // Font types
        MIME_TYPES.put(".woff", "font/woff");
        MIME_TYPES.put(".woff2", "font/woff2");
        MIME_TYPES.put(".ttf", "font/ttf");
        MIME_TYPES.put(".eot", "application/vnd.ms-fontobject");
        
        // Other types
        MIME_TYPES.put(".pdf", "application/pdf");
        MIME_TYPES.put(".zip", "application/zip");
    }
    
    /**
     * Constructs a new RequestRouter.
     * 
     * @param server Reference to the WebServer instance
     */
    public RequestRouter(WebServer server) {
        this.server = server;
        this.chatHandler = new ChatHandler(server);
        this.adminHandler = new AdminHandler(server);
        this.logger = ServerLogger.getInstance();
        this.staticRoot = "public";
    }
    
    /**
     * Routes a request to the appropriate handler.
     * 
     * @param request The HTTP request
     * @param clientIP The client's IP address
     * @return The HTTP response
     */
    public HttpResponse route(HttpRequest request, String clientIP) {
        String path = request.getPath();
        String method = request.getMethod();
        
        try {
            // Route based on path prefix
            if (path.startsWith("/chat")) {
                return chatHandler.handle(request);
            }
            
            if (path.startsWith("/admin")) {
                return adminHandler.handle(request);
            }
            
            // Handle static files
            return handleStaticFile(request);
            
        } catch (Exception e) {
            logger.logError("Error routing request: " + e.getMessage());
            return HttpResponse.serverError("Internal Server Error");
        }
    }
    
    /**
     * Handles static file requests.
     * 
     * @param request The HTTP request
     * @return The HTTP response
     */
    private HttpResponse handleStaticFile(HttpRequest request) {
        String method = request.getMethod();
        
        // Only GET and HEAD allowed for static files
        if (!method.equals("GET") && !method.equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        String path = request.getPath();
        
        // Default to index.html for root
        if (path.equals("/")) {
            path = "/index.html";
        }
        
        // Security: Prevent directory traversal
        if (!SecurityUtils.isSafePath(path)) {
            logger.logError("Directory traversal attempt: " + path);
            return HttpResponse.forbidden("Access Denied");
        }
        
        // Construct file path
        String filePath = staticRoot + path;
        File file = new File(filePath);
        
        // Check if it's a directory
        if (file.isDirectory()) {
            file = new File(filePath + "/index.html");
        }
        
        // Check if file exists
        if (!file.exists() || !file.isFile()) {
            return HttpResponse.notFound("404 Not Found: " + path);
        }
        
        // Check if file is readable
        if (!file.canRead()) {
            return HttpResponse.forbidden("Access Denied");
        }
        
        try {
            // Read file content
            byte[] content = Files.readAllBytes(file.toPath());
            
            // Determine MIME type
            String mimeType = getMimeType(path);
            
            // Build response
            HttpResponse response = new HttpResponse();
            response.setStatusCode(200);
            response.setStatusMessage("OK");
            response.setHeader("Content-Type", mimeType);
            response.setHeader("Cache-Control", "public, max-age=3600");
            response.setBodyBytes(content);
            
            return response;
            
        } catch (IOException e) {
            logger.logError("Error reading file: " + filePath + " - " + e.getMessage());
            return HttpResponse.serverError("Error reading file");
        }
    }
    
    /**
     * Gets the MIME type for a file path.
     * 
     * @param path The file path
     * @return The MIME type
     */
    private String getMimeType(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = path.substring(dotIndex).toLowerCase();
            String mimeType = MIME_TYPES.get(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        return "application/octet-stream";
    }
}
