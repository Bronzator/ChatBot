package database;

import java.sql.*;
import java.util.*;
import logging.ServerLogger;

/**
 * Data Access Object for Message operations.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class MessageDAO {
    
    private final DatabaseManager db;
    private final ServerLogger logger;
    
    public MessageDAO() {
        this.db = DatabaseManager.getInstance();
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Adds a message to a chat.
     * 
     * @param chatId Chat ID
     * @param role Message role (user, assistant, system)
     * @param content Message content
     * @param model Model used (for assistant messages)
     * @param promptTokens Prompt token count
     * @param completionTokens Completion token count
     * @param totalTokens Total token count
     * @return Created message or null
     */
    public Message addMessage(String chatId, String role, String content, 
                              String model, Integer promptTokens, 
                              Integer completionTokens, Integer totalTokens) {
        // Get next sequence number
        int sequenceNum = getNextSequenceNumber(chatId);
        
        String query = """
            INSERT INTO messages (chat_id, role, content, model, prompt_tokens, completion_tokens, total_tokens, sequence_num)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, created_at
            """;
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, chatId);
                stmt.setString(2, role);
                stmt.setString(3, content);
                stmt.setString(4, model);
                stmt.setObject(5, promptTokens);
                stmt.setObject(6, completionTokens);
                stmt.setObject(7, totalTokens);
                stmt.setInt(8, sequenceNum);
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    Message message = new Message();
                    message.setId(rs.getString("id"));
                    message.setChatId(chatId);
                    message.setRole(role);
                    message.setContent(content);
                    message.setModel(model);
                    message.setPromptTokens(promptTokens);
                    message.setCompletionTokens(completionTokens);
                    message.setTotalTokens(totalTokens);
                    message.setSequenceNum(sequenceNum);
                    message.setCreatedAt(rs.getTimestamp("created_at"));
                    
                    rs.close();
                    stmt.close();
                    
                    // Update chat's updated_at
                    updateChatTimestamp(chatId);
                    
                    return message;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to add message: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets all messages for a chat in order.
     */
    public List<Message> getMessagesForChat(String chatId) {
        String query = "SELECT * FROM messages WHERE chat_id = ?::uuid ORDER BY sequence_num ASC";
        return getMessagesByQuery(query, chatId);
    }
    
    /**
     * Gets recent messages for a chat with limit.
     */
    public List<Message> getRecentMessages(String chatId, int limit) {
        String query = """
            SELECT * FROM (
                SELECT * FROM messages WHERE chat_id = ?::uuid ORDER BY sequence_num DESC LIMIT ?
            ) sub ORDER BY sequence_num ASC
            """;
        
        List<Message> messages = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, chatId);
                stmt.setInt(2, limit);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get recent messages: " + e.getMessage());
        }
        return messages;
    }
    
    /**
     * Gets a message by ID.
     */
    public Message findById(String messageId) {
        String query = "SELECT * FROM messages WHERE id = ?::uuid";
        List<Message> messages = getMessagesByQuery(query, messageId);
        return messages.isEmpty() ? null : messages.get(0);
    }
    
    /**
     * Deletes a specific message.
     */
    public boolean deleteMessage(String messageId) {
        String query = "DELETE FROM messages WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, messageId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to delete message: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deletes all messages in a chat.
     */
    public boolean clearChat(String chatId) {
        String query = "DELETE FROM messages WHERE chat_id = ?::uuid";
        try {
            db.executeUpdate(query, chatId);
            logger.logInfo("Cleared all messages from chat: " + chatId);
            return true;
        } catch (SQLException e) {
            logger.logError("Failed to clear chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets message count for a chat.
     */
    public int getMessageCount(String chatId) {
        String query = "SELECT COUNT(*) FROM messages WHERE chat_id = ?::uuid";
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, chatId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    rs.close();
                    stmt.close();
                    return count;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get message count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Gets total message count (for admin).
     */
    public int getTotalMessageCount() {
        String query = "SELECT COUNT(*) FROM messages";
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int count = rs.getInt(1);
                    rs.close();
                    stmt.close();
                    return count;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get total message count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Gets total tokens used by a user.
     */
    public long getTotalTokensForUser(String userId) {
        String query = """
            SELECT COALESCE(SUM(m.total_tokens), 0) 
            FROM messages m 
            JOIN chats c ON m.chat_id = c.id 
            WHERE c.user_id = ?::uuid
            """;
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long total = rs.getLong(1);
                    rs.close();
                    stmt.close();
                    return total;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get total tokens: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Gets messages as context for ChatGPT (formatted for API).
     */
    public List<Map<String, String>> getMessagesAsContext(String chatId, int maxMessages) {
        List<Message> messages = getRecentMessages(chatId, maxMessages);
        List<Map<String, String>> context = new ArrayList<>();
        
        for (Message msg : messages) {
            Map<String, String> entry = new HashMap<>();
            entry.put("role", msg.getRole());
            entry.put("content", msg.getContent());
            context.add(entry);
        }
        
        return context;
    }
    
    // Helper methods
    
    private int getNextSequenceNumber(String chatId) {
        String query = "SELECT COALESCE(MAX(sequence_num), 0) + 1 FROM messages WHERE chat_id = ?::uuid";
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, chatId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int num = rs.getInt(1);
                    rs.close();
                    stmt.close();
                    return num;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get sequence number: " + e.getMessage());
        }
        return 1;
    }
    
    private void updateChatTimestamp(String chatId) {
        String query = "UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid";
        try {
            db.executeUpdate(query, chatId);
        } catch (SQLException e) {
            // Non-critical, just log
            logger.logError("Failed to update chat timestamp: " + e.getMessage());
        }
    }
    
    private List<Message> getMessagesByQuery(String query, Object... params) {
        List<Message> messages = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Database query failed: " + e.getMessage());
        }
        return messages;
    }
    
    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getString("id"));
        message.setChatId(rs.getString("chat_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setModel(rs.getString("model"));
        message.setPromptTokens(rs.getObject("prompt_tokens") != null ? rs.getInt("prompt_tokens") : null);
        message.setCompletionTokens(rs.getObject("completion_tokens") != null ? rs.getInt("completion_tokens") : null);
        message.setTotalTokens(rs.getObject("total_tokens") != null ? rs.getInt("total_tokens") : null);
        message.setSequenceNum(rs.getInt("sequence_num"));
        message.setCreatedAt(rs.getTimestamp("created_at"));
        return message;
    }
}
