package database;

import java.sql.*;
import java.util.*;
import logging.ServerLogger;

/**
 * Data Access Object for Chat operations.
 * 
 * @author ChatBot Project Team
 * @version 2.0
 */
public class ChatDAO {
    
    private final DatabaseManager db;
    private final ServerLogger logger;
    
    public ChatDAO() {
        this.db = DatabaseManager.getInstance();
        this.logger = ServerLogger.getInstance();
    }
    
    /**
     * Creates a new chat for a user.
     * 
     * @param userId User ID
     * @param title Chat title
     * @param model Model to use
     * @return Created chat or null
     */
    public Chat createChat(String userId, String title, String model) {
        String query = """
            INSERT INTO chats (user_id, title, model)
            VALUES (?::uuid, ?, ?)
            RETURNING id, created_at
            """;
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
                stmt.setString(2, title != null ? title : "New Chat");
                stmt.setString(3, model != null ? model : "gpt-3.5-turbo");
                
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    Chat chat = new Chat();
                    chat.setId(rs.getString("id"));
                    chat.setUserId(userId);
                    chat.setTitle(title != null ? title : "New Chat");
                    chat.setModel(model != null ? model : "gpt-3.5-turbo");
                    chat.setCreatedAt(rs.getTimestamp("created_at"));
                    chat.setUpdatedAt(rs.getTimestamp("created_at"));
                    
                    rs.close();
                    stmt.close();
                    logger.logInfo("Created new chat: " + chat.getId() + " for user: " + userId);
                    return chat;
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to create chat: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets a chat by ID.
     */
    public Chat findById(String chatId) {
        String query = "SELECT * FROM chats WHERE id = ?::uuid";
        return findOneByQuery(query, chatId);
    }
    
    /**
     * Gets a chat by ID, ensuring it belongs to the user.
     */
    public Chat findByIdAndUser(String chatId, String userId) {
        String query = "SELECT * FROM chats WHERE id = ?::uuid AND user_id = ?::uuid";
        return findOneByQuery(query, chatId, userId);
    }
    
    /**
     * Gets all chats for a user.
     */
    public List<Chat> getChatsForUser(String userId, boolean includeArchived) {
        String query = includeArchived
            ? "SELECT * FROM chats WHERE user_id = ?::uuid ORDER BY is_pinned DESC, updated_at DESC"
            : "SELECT * FROM chats WHERE user_id = ?::uuid AND is_archived = false ORDER BY is_pinned DESC, updated_at DESC";
        
        List<Chat> chats = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    chats.add(mapResultSetToChat(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get chats: " + e.getMessage());
        }
        return chats;
    }
    
    /**
     * Gets recent chats for a user with limit.
     */
    public List<Chat> getRecentChats(String userId, int limit) {
        String query = """
            SELECT * FROM chats 
            WHERE user_id = ?::uuid AND is_archived = false 
            ORDER BY updated_at DESC 
            LIMIT ?
            """;
        
        List<Chat> chats = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
                stmt.setInt(2, limit);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    chats.add(mapResultSetToChat(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to get recent chats: " + e.getMessage());
        }
        return chats;
    }
    
    /**
     * Updates chat title.
     */
    public boolean updateTitle(String chatId, String title) {
        String query = "UPDATE chats SET title = ? WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, title, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to update chat title: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Updates chat model.
     */
    public boolean updateModel(String chatId, String model) {
        String query = "UPDATE chats SET model = ? WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, model, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to update chat model: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Archives a chat.
     */
    public boolean archiveChat(String chatId) {
        String query = "UPDATE chats SET is_archived = true WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to archive chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Unarchives a chat.
     */
    public boolean unarchiveChat(String chatId) {
        String query = "UPDATE chats SET is_archived = false WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to unarchive chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Pins a chat.
     */
    public boolean pinChat(String chatId) {
        String query = "UPDATE chats SET is_pinned = true WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to pin chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Unpins a chat.
     */
    public boolean unpinChat(String chatId) {
        String query = "UPDATE chats SET is_pinned = false WHERE id = ?::uuid";
        try {
            return db.executeUpdate(query, chatId) > 0;
        } catch (SQLException e) {
            logger.logError("Failed to unpin chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deletes a chat and all its messages.
     */
    public boolean deleteChat(String chatId) {
        String query = "DELETE FROM chats WHERE id = ?::uuid";
        try {
            boolean result = db.executeUpdate(query, chatId) > 0;
            if (result) {
                logger.logInfo("Deleted chat: " + chatId);
            }
            return result;
        } catch (SQLException e) {
            logger.logError("Failed to delete chat: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets total chat count for a user.
     */
    public int getChatCount(String userId) {
        String query = "SELECT COUNT(*) FROM chats WHERE user_id = ?::uuid";
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
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
            logger.logError("Failed to get chat count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Gets total chat count (for admin).
     */
    public int getTotalChatCount() {
        String query = "SELECT COUNT(*) FROM chats";
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
            logger.logError("Failed to get total chat count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Searches chats for a user.
     */
    public List<Chat> searchChats(String userId, String searchTerm) {
        String query = """
            SELECT DISTINCT c.* FROM chats c
            LEFT JOIN messages m ON c.id = m.chat_id
            WHERE c.user_id = ?::uuid 
            AND (c.title ILIKE ? OR m.content ILIKE ?)
            ORDER BY c.updated_at DESC
            LIMIT 50
            """;
        
        List<Chat> chats = new ArrayList<>();
        String likePattern = "%" + searchTerm + "%";
        
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, userId);
                stmt.setString(2, likePattern);
                stmt.setString(3, likePattern);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    chats.add(mapResultSetToChat(rs));
                }
                rs.close();
                stmt.close();
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Failed to search chats: " + e.getMessage());
        }
        return chats;
    }
    
    // Helper methods
    
    private Chat findOneByQuery(String query, Object... params) {
        try {
            Connection conn = db.getConnection();
            try {
                PreparedStatement stmt = conn.prepareStatement(query);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                ResultSet rs = stmt.executeQuery();
                Chat chat = null;
                if (rs.next()) {
                    chat = mapResultSetToChat(rs);
                }
                rs.close();
                stmt.close();
                return chat;
            } finally {
                db.releaseConnection(conn);
            }
        } catch (SQLException e) {
            logger.logError("Database query failed: " + e.getMessage());
            return null;
        }
    }
    
    private Chat mapResultSetToChat(ResultSet rs) throws SQLException {
        Chat chat = new Chat();
        chat.setId(rs.getString("id"));
        chat.setUserId(rs.getString("user_id"));
        chat.setTitle(rs.getString("title"));
        chat.setModel(rs.getString("model"));
        chat.setSystemPrompt(rs.getString("system_prompt"));
        chat.setTemperature(rs.getDouble("temperature"));
        chat.setArchived(rs.getBoolean("is_archived"));
        chat.setPinned(rs.getBoolean("is_pinned"));
        chat.setCreatedAt(rs.getTimestamp("created_at"));
        chat.setUpdatedAt(rs.getTimestamp("updated_at"));
        chat.setMessageCount(rs.getInt("message_count"));
        chat.setTotalTokens(rs.getInt("total_tokens"));
        return chat;
    }
}
