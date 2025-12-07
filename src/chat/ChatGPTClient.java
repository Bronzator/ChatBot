package chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

import logging.ServerLogger;
import server.RequestParser;

/**
 * Client for interacting with the OpenAI ChatGPT API.
 * 
 * This class handles:
 * - Sending chat completion requests to OpenAI
 * - Parsing API responses
 * - Extracting metadata (tokens, model, etc.)
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ChatGPTClient {
    
    /** OpenAI API endpoint */
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    
    /** Connection timeout in milliseconds */
    private static final int CONNECT_TIMEOUT = 30000;
    
    /** Read timeout in milliseconds */
    private static final int READ_TIMEOUT = 60000;
    
    /** OpenAI API key */
    private final String apiKey;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /**
     * Constructs a new ChatGPTClient.
     * 
     * @param apiKey The OpenAI API key
     */
    public ChatGPTClient(String apiKey) {
        this.apiKey = apiKey;
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Sends a chat completion request to OpenAI.
     * 
     * @param session The chat session containing the prompt
     * @param model The model to use (e.g., "gpt-3.5-turbo", "gpt-4")
     * @return true if successful, false otherwise
     */
    public boolean sendChatRequest(ChatSession session, String model) {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.logError("OpenAI API key not configured");
            session.setResponse("Error: OpenAI API key not configured");
            return false;
        }
        
        try {
            // Build request body with conversation history if available
            String requestBody = buildRequestBody(session.getPrompt(), model, session.getConversationHistory());
            
            // Create connection
            URL url = new URL(API_URL);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);
            
            // Set headers
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                String responseBody = readResponse(connection.getInputStream());
                parseResponse(session, responseBody);
                logger.logInfo("ChatGPT request successful for session: " + session.getId());
                return true;
            } else {
                String errorBody = readResponse(connection.getErrorStream());
                logger.logError("ChatGPT API error: " + responseCode + " - " + errorBody);
                session.setResponse("Error: API returned status " + responseCode);
                return false;
            }
            
        } catch (SocketTimeoutException e) {
            logger.logError("ChatGPT request timeout: " + e.getMessage());
            session.setResponse("Error: Request timeout");
            return false;
        } catch (IOException e) {
            logger.logError("ChatGPT request failed: " + e.getMessage());
            session.setResponse("Error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Builds the JSON request body for the ChatGPT API.
     * Includes conversation history for context if available.
     * 
     * @param prompt The user's prompt
     * @param model The model to use
     * @param conversationHistory Previous messages for context
     * @return JSON request body
     */
    private String buildRequestBody(String prompt, String model, java.util.List<java.util.Map<String, String>> conversationHistory) {
        StringBuilder messagesJson = new StringBuilder("[");
        
        // Add conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (java.util.Map<String, String> msg : conversationHistory) {
                messagesJson.append("{\"role\":\"").append(escapeJson(msg.get("role")))
                    .append("\",\"content\":\"").append(escapeJson(msg.get("content")))
                    .append("\"},");
            }
        }
        
        // Add current user message
        messagesJson.append("{\"role\":\"user\",\"content\":\"")
            .append(escapeJson(prompt))
            .append("\"}]");
        
        return String.format(
            "{\"model\":\"%s\",\"messages\":%s}",
            model,
            messagesJson.toString()
        );
    }
    
    /**
     * Builds the JSON request body (legacy without history).
     */
    private String buildRequestBody(String prompt, String model) {
        return buildRequestBody(prompt, model, null);
    }
    
    /**
     * Parses the API response and updates the session.
     * 
     * @param session The chat session to update
     * @param responseBody The raw API response body
     */
    private void parseResponse(ChatSession session, String responseBody) {
        try {
            // Extract response content
            String content = extractJsonValue(responseBody, "content");
            if (content != null) {
                // Unescape the content
                content = unescapeJson(content);
                session.setResponse(content);
            } else {
                session.setResponse("Error: Could not parse response");
            }
            
            // Extract metadata
            ResponseMetadata metadata = new ResponseMetadata();
            
            // Model
            String model = extractJsonValue(responseBody, "model");
            if (model != null) {
                metadata.setModel(model);
            }
            
            // Response ID
            String id = extractJsonValue(responseBody, "id");
            if (id != null) {
                metadata.setResponseId(id);
            }
            
            // Created timestamp
            String created = extractJsonValue(responseBody, "created");
            if (created != null) {
                try {
                    metadata.setCreatedTimestamp(Long.parseLong(created));
                } catch (NumberFormatException e) {
                    metadata.setCreatedTimestamp(System.currentTimeMillis() / 1000);
                }
            }
            
            // Token counts (from usage object)
            String promptTokens = extractNestedJsonValue(responseBody, "usage", "prompt_tokens");
            String completionTokens = extractNestedJsonValue(responseBody, "usage", "completion_tokens");
            String totalTokens = extractNestedJsonValue(responseBody, "usage", "total_tokens");
            
            if (promptTokens != null) {
                try {
                    metadata.setPromptTokens(Integer.parseInt(promptTokens));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            
            if (completionTokens != null) {
                try {
                    metadata.setCompletionTokens(Integer.parseInt(completionTokens));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            
            if (totalTokens != null) {
                try {
                    metadata.setTotalTokens(Integer.parseInt(totalTokens));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            
            session.setMetadata(metadata);
            
        } catch (Exception e) {
            logger.logError("Error parsing ChatGPT response: " + e.getMessage());
            session.setResponse("Error: Failed to parse response");
        }
    }
    
    /**
     * Reads the response body from an input stream.
     * 
     * @param inputStream The input stream
     * @return The response body as string
     * @throws IOException If reading fails
     */
    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    /**
     * Extracts a value from JSON (simple parser for flat values).
     * 
     * @param json The JSON string
     * @param key The key to extract
     * @return The value, or null if not found
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        
        if (keyIndex == -1) {
            return null;
        }
        
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) {
            return null;
        }
        
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) {
            return null;
        }
        
        char firstChar = json.charAt(valueStart);
        
        if (firstChar == '"') {
            // String value - need to handle escape sequences
            int valueEnd = valueStart + 1;
            boolean escaped = false;
            while (valueEnd < json.length()) {
                char c = json.charAt(valueEnd);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                }
                valueEnd++;
            }
            return json.substring(valueStart + 1, valueEnd);
        } else {
            // Number, boolean, or null
            int valueEnd = valueStart;
            while (valueEnd < json.length()) {
                char c = json.charAt(valueEnd);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
    }
    
    /**
     * Extracts a nested value from JSON.
     * 
     * @param json The JSON string
     * @param parentKey The parent object key
     * @param childKey The child key to extract
     * @return The value, or null if not found
     */
    private String extractNestedJsonValue(String json, String parentKey, String childKey) {
        String searchKey = "\"" + parentKey + "\"";
        int keyIndex = json.indexOf(searchKey);
        
        if (keyIndex == -1) {
            return null;
        }
        
        // Find the opening brace of the nested object
        int braceIndex = json.indexOf('{', keyIndex);
        if (braceIndex == -1) {
            return null;
        }
        
        // Find the closing brace (simple approach - may not work for deeply nested)
        int closingBrace = findMatchingBrace(json, braceIndex);
        if (closingBrace == -1) {
            return null;
        }
        
        // Extract the nested object
        String nested = json.substring(braceIndex, closingBrace + 1);
        
        // Extract the child value
        return extractJsonValue(nested, childKey);
    }
    
    /**
     * Finds the matching closing brace.
     * 
     * @param json The JSON string
     * @param openIndex The index of the opening brace
     * @return The index of the closing brace, or -1
     */
    private int findMatchingBrace(String json, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = openIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        
        return -1;
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
        
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * Unescapes a JSON string.
     * 
     * @param str The string to unescape
     * @return Unescaped string
     */
    private String unescapeJson(String str) {
        if (str == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            
            if (escaped) {
                switch (c) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (i + 4 < str.length()) {
                            try {
                                int code = Integer.parseInt(str.substring(i + 1, i + 5), 16);
                                sb.append((char) code);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                        break;
                    default:
                        sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
}
