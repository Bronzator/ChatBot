package chat;

/**
 * Represents metadata from a ChatGPT API response.
 * 
 * Contains information about:
 * - The model used for generation
 * - Token usage (prompt, completion, total)
 * - Timestamp of the response
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ResponseMetadata {
    
    /** The model used for generation */
    private String model;
    
    /** Number of tokens in the prompt */
    private int promptTokens;
    
    /** Number of tokens in the completion */
    private int completionTokens;
    
    /** Total tokens used */
    private int totalTokens;
    
    /** Unix timestamp when response was created */
    private long createdTimestamp;
    
    /** Response ID from OpenAI */
    private String responseId;
    
    /**
     * Constructs a new ResponseMetadata with default values.
     */
    public ResponseMetadata() {
        this.createdTimestamp = System.currentTimeMillis() / 1000;
    }
    
    /**
     * Constructs a ResponseMetadata with specified values.
     * 
     * @param model The model name
     * @param promptTokens Prompt token count
     * @param completionTokens Completion token count
     * @param totalTokens Total token count
     * @param createdTimestamp Creation timestamp
     */
    public ResponseMetadata(String model, int promptTokens, int completionTokens, 
                           int totalTokens, long createdTimestamp) {
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.createdTimestamp = createdTimestamp;
    }
    
    // ==================== Getters and Setters ====================
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public int getPromptTokens() {
        return promptTokens;
    }
    
    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }
    
    public int getCompletionTokens() {
        return completionTokens;
    }
    
    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
    
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    public long getCreatedTimestamp() {
        return createdTimestamp;
    }
    
    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
    
    public String getResponseId() {
        return responseId;
    }
    
    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }
    
    /**
     * Converts metadata to JSON string.
     * @return JSON representation
     */
    public String toJson() {
        return String.format(
            "{\"model\":\"%s\",\"promptTokens\":%d,\"completionTokens\":%d," +
            "\"totalTokens\":%d,\"created\":%d}",
            model != null ? model : "",
            promptTokens,
            completionTokens,
            totalTokens,
            createdTimestamp
        );
    }
    
    @Override
    public String toString() {
        return String.format("ResponseMetadata{model='%s', tokens=%d/%d/%d, created=%d}",
            model, promptTokens, completionTokens, totalTokens, createdTimestamp);
    }
}
