package database;

import java.sql.Timestamp;

/**
 * Message entity class.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class Message {
    
    private String id;
    private String chatId;
    private String role;
    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private int sequenceNum;
    private Timestamp createdAt;
    
    // Getters and Setters
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    
    public int getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(int sequenceNum) { this.sequenceNum = sequenceNum; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    /**
     * Converts to JSON for API responses.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"chatId\":\"").append(escapeJson(chatId)).append("\",");
        sb.append("\"role\":\"").append(escapeJson(role)).append("\",");
        sb.append("\"content\":\"").append(escapeJson(content)).append("\",");
        sb.append("\"model\":").append(model != null ? "\"" + escapeJson(model) + "\"" : "null").append(",");
        sb.append("\"promptTokens\":").append(promptTokens != null ? promptTokens : "null").append(",");
        sb.append("\"completionTokens\":").append(completionTokens != null ? completionTokens : "null").append(",");
        sb.append("\"totalTokens\":").append(totalTokens != null ? totalTokens : "null").append(",");
        sb.append("\"createdAt\":\"").append(createdAt != null ? createdAt.toString() : "").append("\"");
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
