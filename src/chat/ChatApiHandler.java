package chat;

import java.util.*;
import server.*;
import database.*;
import auth.*;
import logging.ServerLogger;

/**
 * Handles chat API endpoints with user authentication and persistence.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class ChatApiHandler {
    
    private final ChatDAO chatDAO;
    private final MessageDAO messageDAO;
    private final UserAuthHandler authHandler;
    private final ChatGPTClient chatClient;
    private final ServerLogger logger;
    private final WebServer server;
    
    public ChatApiHandler(WebServer server) {
        this.server = server;
        this.chatDAO = new ChatDAO();
        this.messageDAO = new MessageDAO();
        this.authHandler = new UserAuthHandler();
        this.chatClient = new ChatGPTClient(server.getConfig().getApiKey());
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Handles chat API requests.
     */
    public HttpResponse handle(HttpRequest request) {
        String path = request.getPath();
        String method = request.getMethod();
        
        // Get authenticated user
        User user = authHandler.getUserFromRequest(request);
        if (user == null) {
            return HttpResponse.unauthorized("{\"error\":\"Authentication required\"}");
        }
        
        // Check if chat is enabled
        if (!server.isChatEnabled() && !user.isAdmin()) {
            return HttpResponse.forbidden("{\"error\":\"Chat is currently disabled\"}");
        }
        
        // Route requests
        if (path.equals("/api/chats")) {
            return switch (method) {
                case "GET" -> handleGetChats(user);
                case "POST" -> handleCreateChat(request, user);
                default -> HttpResponse.methodNotAllowed();
            };
        } else if (path.matches("/api/chats/[a-f0-9-]+")) {
            String chatId = path.substring("/api/chats/".length());
            return switch (method) {
                case "GET" -> handleGetChat(chatId, user);
                case "PUT" -> handleUpdateChat(request, chatId, user);
                case "DELETE" -> handleDeleteChat(chatId, user);
                default -> HttpResponse.methodNotAllowed();
            };
        } else if (path.matches("/api/chats/[a-f0-9-]+/messages")) {
            String chatId = path.substring("/api/chats/".length(), path.indexOf("/messages"));
            return switch (method) {
                case "GET" -> handleGetMessages(chatId, user);
                case "POST" -> handleSendMessage(request, chatId, user);
                default -> HttpResponse.methodNotAllowed();
            };
        } else if (path.equals("/api/chats/search")) {
            return method.equals("GET") ? handleSearchChats(request, user) : HttpResponse.methodNotAllowed();
        }
        
        return HttpResponse.notFound("{\"error\":\"Endpoint not found\"}");
    }
    
    /**
     * Gets all chats for user.
     */
    private HttpResponse handleGetChats(User user) {
        List<Chat> chats = chatDAO.getChatsForUser(user.getId(), false);
        
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Chat chat : chats) {
            if (!first) json.append(",");
            first = false;
            json.append(chat.toListJson());
        }
        json.append("]");
        
        HttpResponse response = HttpResponse.ok(json.toString());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Creates a new chat.
     */
    private HttpResponse handleCreateChat(HttpRequest request, User user) {
        String body = request.getBody();
        String title = extractJsonValue(body, "title");
        String model = extractJsonValue(body, "model");
        
        if (model == null || model.isEmpty()) {
            model = server.getCurrentModel();
        }
        
        Chat chat = chatDAO.createChat(user.getId(), title, model);
        if (chat == null) {
            return HttpResponse.serverError("{\"error\":\"Failed to create chat\"}");
        }
        
        logger.logInfo("Created new chat: " + chat.getId() + " for user: " + user.getUsername());
        
        HttpResponse response = HttpResponse.createdJson(chat.toJson());
        response.setHeader("Location", "/api/chats/" + chat.getId());
        return response;
    }
    
    /**
     * Gets a specific chat with messages.
     */
    private HttpResponse handleGetChat(String chatId, User user) {
        Chat chat = chatDAO.findByIdAndUser(chatId, user.getId());
        if (chat == null) {
            return HttpResponse.notFound("{\"error\":\"Chat not found\"}");
        }
        
        List<Message> messages = messageDAO.getMessagesForChat(chatId);
        
        StringBuilder json = new StringBuilder("{");
        json.append("\"chat\":").append(chat.toJson()).append(",");
        json.append("\"messages\":[");
        boolean first = true;
        for (Message msg : messages) {
            if (!first) json.append(",");
            first = false;
            json.append(msg.toJson());
        }
        json.append("]}");
        
        HttpResponse response = HttpResponse.ok(json.toString());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Updates a chat (title, archive, pin).
     */
    private HttpResponse handleUpdateChat(HttpRequest request, String chatId, User user) {
        Chat chat = chatDAO.findByIdAndUser(chatId, user.getId());
        if (chat == null) {
            return HttpResponse.notFound("{\"error\":\"Chat not found\"}");
        }
        
        String body = request.getBody();
        String title = extractJsonValue(body, "title");
        String archived = extractJsonValue(body, "isArchived");
        String pinned = extractJsonValue(body, "isPinned");
        String model = extractJsonValue(body, "model");
        
        if (title != null) {
            chatDAO.updateTitle(chatId, title);
        }
        if (archived != null) {
            if ("true".equals(archived)) {
                chatDAO.archiveChat(chatId);
            } else {
                chatDAO.unarchiveChat(chatId);
            }
        }
        if (pinned != null) {
            if ("true".equals(pinned)) {
                chatDAO.pinChat(chatId);
            } else {
                chatDAO.unpinChat(chatId);
            }
        }
        if (model != null) {
            chatDAO.updateModel(chatId, model);
        }
        
        // Return updated chat
        chat = chatDAO.findById(chatId);
        HttpResponse response = HttpResponse.ok(chat.toJson());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Deletes a chat.
     */
    private HttpResponse handleDeleteChat(String chatId, User user) {
        Chat chat = chatDAO.findByIdAndUser(chatId, user.getId());
        if (chat == null) {
            return HttpResponse.notFound("{\"error\":\"Chat not found\"}");
        }
        
        if (chatDAO.deleteChat(chatId)) {
            logger.logInfo("Deleted chat: " + chatId);
            return HttpResponse.ok("{\"message\":\"Chat deleted\"}");
        } else {
            return HttpResponse.serverError("{\"error\":\"Failed to delete chat\"}");
        }
    }
    
    /**
     * Gets messages for a chat.
     */
    private HttpResponse handleGetMessages(String chatId, User user) {
        Chat chat = chatDAO.findByIdAndUser(chatId, user.getId());
        if (chat == null) {
            return HttpResponse.notFound("{\"error\":\"Chat not found\"}");
        }
        
        List<Message> messages = messageDAO.getMessagesForChat(chatId);
        
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Message msg : messages) {
            if (!first) json.append(",");
            first = false;
            json.append(msg.toJson());
        }
        json.append("]");
        
        HttpResponse response = HttpResponse.ok(json.toString());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Sends a message and gets AI response.
     */
    private HttpResponse handleSendMessage(HttpRequest request, String chatId, User user) {
        Chat chat = chatDAO.findByIdAndUser(chatId, user.getId());
        if (chat == null) {
            return HttpResponse.notFound("{\"error\":\"Chat not found\"}");
        }
        
        String body = request.getBody();
        String content = extractJsonValue(body, "content");
        
        if (content == null || content.trim().isEmpty()) {
            return HttpResponse.badRequest("{\"error\":\"Message content required\"}");
        }
        
        // Save user message
        Message userMessage = messageDAO.addMessage(chatId, "user", content, null, null, null, null);
        if (userMessage == null) {
            return HttpResponse.serverError("{\"error\":\"Failed to save message\"}");
        }
        
        // Get conversation history for context
        List<Map<String, String>> context = messageDAO.getMessagesAsContext(chatId, 20);
        
        // Auto-generate title from first message if needed
        if (chat.getTitle().equals("New Chat") && messageDAO.getMessageCount(chatId) == 1) {
            String autoTitle = content.length() > 50 ? content.substring(0, 47) + "..." : content;
            chatDAO.updateTitle(chatId, autoTitle);
        }
        
        // Create session for ChatGPT
        ChatSession session = new ChatSession(chatId);
        session.setPrompt(content);
        session.setConversationHistory(context);
        
        // Get AI response
        String model = chat.getModel();
        boolean success = chatClient.sendChatRequest(session, model);
        
        Message aiMessage = null;
        if (success) {
            ResponseMetadata metadata = session.getMetadata();
            aiMessage = messageDAO.addMessage(
                chatId, 
                "assistant", 
                session.getResponse(),
                model,
                metadata != null ? metadata.getPromptTokens() : null,
                metadata != null ? metadata.getCompletionTokens() : null,
                metadata != null ? metadata.getTotalTokens() : null
            );
            
            logger.logInfo("Message sent in chat: " + chatId + " by user: " + user.getUsername());
        } else {
            // Save error as AI response
            aiMessage = messageDAO.addMessage(chatId, "assistant", 
                "Sorry, I encountered an error processing your request. " + session.getResponse(), 
                model, null, null, null);
        }
        
        // Build response
        StringBuilder json = new StringBuilder("{");
        json.append("\"userMessage\":").append(userMessage.toJson()).append(",");
        json.append("\"aiMessage\":").append(aiMessage != null ? aiMessage.toJson() : "null");
        json.append("}");
        
        HttpResponse response = HttpResponse.ok(json.toString());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    /**
     * Searches chats.
     */
    private HttpResponse handleSearchChats(HttpRequest request, User user) {
        String query = request.getQueryParam("q");
        if (query == null || query.trim().isEmpty()) {
            return HttpResponse.badRequest("{\"error\":\"Search query required\"}");
        }
        
        List<Chat> chats = chatDAO.searchChats(user.getId(), query);
        
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Chat chat : chats) {
            if (!first) json.append(",");
            first = false;
            json.append(chat.toListJson());
        }
        json.append("]");
        
        HttpResponse response = HttpResponse.ok(json.toString());
        response.setHeader("Content-Type", "application/json");
        return response;
    }
    
    // Helper method
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
            int valueEnd = valueStart + 1;
            while (valueEnd < json.length() && json.charAt(valueEnd) != '"') {
                if (json.charAt(valueEnd) == '\\') valueEnd++; // Skip escaped chars
                valueEnd++;
            }
            return json.substring(valueStart + 1, valueEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        } else if (json.charAt(valueStart) == 'n') {
            return null;
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
