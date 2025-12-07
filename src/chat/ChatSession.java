package chat;

import java.util.*;

/**
 * Represents a single chat session with ChatGPT.
 * 
 * Contains:
 * - Unique session ID
 * - User's prompt
 * - ChatGPT's response
 * - Response metadata (tokens, model, etc.)
 * - Conversation history for context
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class ChatSession {
    
    /** Unique session identifier */
    private final String id;
    
    /** User's input prompt */
    private String prompt;
    
    /** ChatGPT's response text */
    private String response;
    
    /** Response metadata */
    private ResponseMetadata metadata;
    
    /** Session creation timestamp */
    private final long createdAt;
    
    /** Whether the session has received a response */
    private boolean completed;
    
    /** Conversation history for context */
    private List<Map<String, String>> conversationHistory;
    
    /**
     * Constructs a new ChatSession with a generated ID.
     */
    public ChatSession() {
        this.id = generateId();
        this.createdAt = System.currentTimeMillis();
        this.completed = false;
        this.conversationHistory = new ArrayList<>();
    }
    
    /**
     * Constructs a new ChatSession with specified ID.
     * 
     * @param id The session ID
     */
    public ChatSession(String id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.completed = false;
        this.conversationHistory = new ArrayList<>();
    }
    
    /**
     * Constructs a new ChatSession with prompt.
     * 
     * @param prompt The user's prompt
     */
    public static ChatSession create(String prompt) {
        ChatSession session = new ChatSession();
        session.setPrompt(prompt);
        return session;
    }
    
    /**
     * Generates a unique session ID.
     * 
     * @return A unique ID string
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    // ==================== Getters and Setters ====================
    
    public String getId() {
        return id;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
        this.completed = true;
    }
    
    public ResponseMetadata getMetadata() {
        return metadata;
    }
    
    public void setMetadata(ResponseMetadata metadata) {
        this.metadata = metadata;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public List<Map<String, String>> getConversationHistory() {
        return conversationHistory;
    }
    
    public void setConversationHistory(List<Map<String, String>> history) {
        this.conversationHistory = history != null ? history : new ArrayList<>();
    }
    
    public void addToHistory(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        conversationHistory.add(message);
    }
    
    /**
     * Gets a preview of the prompt (first 50 characters).
     * 
     * @return Truncated prompt
     */
    public String getPromptPreview() {
        if (prompt == null) {
            return "";
        }
        if (prompt.length() <= 50) {
            return prompt;
        }
        return prompt.substring(0, 47) + "...";
    }
    
    /**
     * Converts session to JSON string.
     * @return JSON representation
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(escapeJson(id)).append("\",");
        json.append("\"prompt\":\"").append(escapeJson(prompt != null ? prompt : "")).append("\",");
        json.append("\"promptPreview\":\"").append(escapeJson(getPromptPreview())).append("\",");
        json.append("\"response\":\"").append(escapeJson(response != null ? response : "")).append("\",");
        json.append("\"createdAt\":").append(createdAt).append(",");
        json.append("\"completed\":").append(completed);
        
        if (metadata != null) {
            json.append(",\"model\":\"").append(escapeJson(metadata.getModel())).append("\"");
            json.append(",\"totalTokens\":").append(metadata.getTotalTokens());
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Converts session to summary JSON (for admin list view).
     * @return JSON summary
     */
    public String toSummaryJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(escapeJson(id)).append("\",");
        json.append("\"promptPreview\":\"").append(escapeJson(getPromptPreview())).append("\",");
        json.append("\"model\":\"").append(escapeJson(metadata != null ? metadata.getModel() : "")).append("\",");
        json.append("\"totalTokens\":").append(metadata != null ? metadata.getTotalTokens() : 0).append(",");
        json.append("\"timestamp\":").append(createdAt);
        json.append("}");
        return json.toString();
    }
    
    /**
     * Escapes a string for JSON.
     * 
     * @param str The string to escape
     * @return Escaped string
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    @Override
    public String toString() {
        return String.format("ChatSession{id='%s', prompt='%s', completed=%b}",
            id, getPromptPreview(), completed);
    }
}
