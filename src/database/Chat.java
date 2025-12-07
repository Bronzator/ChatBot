package database;

import java.sql.Timestamp;

/**
 * Chat entity class.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class Chat {
    
    private String id;
    private String userId;
    private String title;
    private String model;
    private String systemPrompt;
    private double temperature;
    private boolean isArchived;
    private boolean isPinned;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private int messageCount;
    private int totalTokens;
    
    // Getters and Setters
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }
    
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    
    /**
     * Converts to JSON for API responses.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"userId\":\"").append(escapeJson(userId)).append("\",");
        sb.append("\"title\":\"").append(escapeJson(title)).append("\",");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"systemPrompt\":").append(systemPrompt != null ? "\"" + escapeJson(systemPrompt) + "\"" : "null").append(",");
        sb.append("\"temperature\":").append(temperature).append(",");
        sb.append("\"isArchived\":").append(isArchived).append(",");
        sb.append("\"isPinned\":").append(isPinned).append(",");
        sb.append("\"createdAt\":\"").append(createdAt != null ? createdAt.toString() : "").append("\",");
        sb.append("\"updatedAt\":\"").append(updatedAt != null ? updatedAt.toString() : "").append("\",");
        sb.append("\"messageCount\":").append(messageCount).append(",");
        sb.append("\"totalTokens\":").append(totalTokens);
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Converts to compact JSON for list views.
     */
    public String toListJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"title\":\"").append(escapeJson(title)).append("\",");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"isPinned\":").append(isPinned).append(",");
        sb.append("\"updatedAt\":\"").append(updatedAt != null ? updatedAt.toString() : "").append("\",");
        sb.append("\"messageCount\":").append(messageCount);
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
