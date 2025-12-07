package chat;

import server.*;
import logging.ServerLogger;

/**
 * Handles HTTP requests for the /chat API endpoints.
 * 
 * Endpoints:
 * - POST /chat - Create new chat session and get ChatGPT response
 * - GET /chat/{id} - Retrieve chat response by session ID
 * - HEAD /chat/{id} - Get headers only for chat session
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ChatHandler {
    
    /** Reference to the web server */
    private final WebServer server;
    
    /** Chat session manager */
    private final ChatSessionManager sessionManager;
    
    /** ChatGPT API client */
    private final ChatGPTClient chatClient;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /**
     * Constructs a new ChatHandler.
     * 
     * @param server Reference to the WebServer instance
     */
    public ChatHandler(WebServer server) {
        this.server = server;
        this.sessionManager = ChatSessionManager.getInstance();
        this.chatClient = new ChatGPTClient(server.getConfig().getApiKey());
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Handles a chat-related HTTP request.
     * 
     * @param request The HTTP request
     * @return The HTTP response
     */
    public HttpResponse handle(HttpRequest request) {
        String method = request.getMethod();
        String path = request.getPath();
        
        // Check if chat is enabled
        if (!server.isChatEnabled() && method.equals("POST")) {
            return HttpResponse.serviceUnavailable("Chat service is currently disabled");
        }
        
        // Route based on path
        if (path.equals("/chat")) {
            return handleChatRoot(request);
        }
        
        // Check for /chat/{id} pattern
        if (path.startsWith("/chat/")) {
            String sessionId = path.substring(6); // Remove "/chat/"
            if (!sessionId.isEmpty() && !sessionId.contains("/")) {
                return handleChatSession(request, sessionId);
            }
        }
        
        return HttpResponse.notFound("Chat endpoint not found");
    }
    
    /**
     * Handles requests to /chat (root chat endpoint).
     * 
     * POST - Create new chat session
     * Other methods - 405 Method Not Allowed
     * 
     * @param request The HTTP request
     * @return The HTTP response
     */
    private HttpResponse handleChatRoot(HttpRequest request) {
        String method = request.getMethod();
        
        if (!method.equals("POST")) {
            return HttpResponse.methodNotAllowed("POST");
        }
        
        // Extract prompt from request body
        String prompt = extractPrompt(request);
        
        if (prompt == null || prompt.trim().isEmpty()) {
            return HttpResponse.badRequest("Missing or empty prompt");
        }
        
        try {
            // Create session
            ChatSession session = sessionManager.createSession(prompt.trim());
            logger.logInfo("Created chat session: " + session.getId());
            
            // Send to ChatGPT
            String model = server.getCurrentModel();
            boolean success = chatClient.sendChatRequest(session, model);
            
            if (!success) {
                logger.logError("ChatGPT request failed for session: " + session.getId());
                // Session still created, but with error response
            }
            
            // Update session in manager
            sessionManager.updateSession(session);
            
            // Return 201 Created with Location header
            String location = "/chat/" + session.getId();
            HttpResponse response = HttpResponse.created(location, "id=" + session.getId());
            
            return response;
            
        } catch (Exception e) {
            logger.logError("Error processing chat request: " + e.getMessage());
            return HttpResponse.serverError("Error processing request");
        }
    }
    
    /**
     * Handles requests to /chat/{id}.
     * 
     * GET - Retrieve chat response
     * HEAD - Get headers only
     * Other methods - 405 Method Not Allowed
     * 
     * @param request The HTTP request
     * @param sessionId The session ID
     * @return The HTTP response
     */
    private HttpResponse handleChatSession(HttpRequest request, String sessionId) {
        String method = request.getMethod();
        
        if (!method.equals("GET") && !method.equals("HEAD")) {
            return HttpResponse.methodNotAllowed("GET, HEAD");
        }
        
        // Look up session
        ChatSession session = sessionManager.getSession(sessionId);
        
        if (session == null) {
            return HttpResponse.notFound("Chat session not found: " + sessionId);
        }
        
        // Build response
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        
        // Add metadata headers
        ResponseMetadata metadata = session.getMetadata();
        if (metadata != null) {
            response.setHeader("X-Model", metadata.getModel() != null ? metadata.getModel() : "");
            response.setHeader("X-Prompt-Tokens", String.valueOf(metadata.getPromptTokens()));
            response.setHeader("X-Completion-Tokens", String.valueOf(metadata.getCompletionTokens()));
            response.setHeader("X-Total-Tokens", String.valueOf(metadata.getTotalTokens()));
            response.setHeader("X-Created", String.valueOf(metadata.getCreatedTimestamp()));
        }
        
        // Set body (will be omitted for HEAD by ResponseBuilder)
        String responseText = session.getResponse();
        response.setBody(responseText != null ? responseText : "");
        
        return response;
    }
    
    /**
     * Extracts the prompt from the request body.
     * 
     * Supports:
     * - Raw text body
     * - JSON: {"prompt": "..."}
     * - Form data: prompt=...
     * 
     * @param request The HTTP request
     * @return The extracted prompt, or null
     */
    private String extractPrompt(HttpRequest request) {
        String body = request.getBody();
        
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        String contentType = request.getContentType();
        
        // JSON body
        if (contentType != null && contentType.contains("application/json")) {
            String prompt = RequestParser.parseJsonValue(body, "prompt");
            if (prompt != null) {
                return prompt;
            }
        }
        
        // Form data
        if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            java.util.Map<String, String> formData = RequestParser.parseFormData(body);
            String prompt = formData.get("prompt");
            if (prompt != null) {
                return prompt;
            }
        }
        
        // Raw text - return entire body as prompt
        return body.trim();
    }
}
