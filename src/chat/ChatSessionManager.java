package chat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chat sessions in memory.
 * 
 * This class provides thread-safe storage and retrieval of chat sessions.
 * It uses ConcurrentHashMap for thread safety without explicit synchronization.
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ChatSessionManager {
    
    /** Singleton instance */
    private static ChatSessionManager instance;
    
    /** Thread-safe session storage */
    private final ConcurrentHashMap<String, ChatSession> sessions;
    
    /** Maximum number of sessions to store */
    private static final int MAX_SESSIONS = 10000;
    
    /** Session expiry time in milliseconds (24 hours) */
    private static final long SESSION_EXPIRY_MS = 24 * 60 * 60 * 1000;
    
    /**
     * Private constructor for singleton pattern.
     */
    private ChatSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets the singleton instance of ChatSessionManager.
     * 
     * @return The ChatSessionManager instance
     */
    public static synchronized ChatSessionManager getInstance() {
        if (instance == null) {
            instance = new ChatSessionManager();
        }
        return instance;
    }
    
    /**
     * Creates a new chat session with the given prompt.
     * 
     * @param prompt The user's prompt
     * @return The created ChatSession
     */
    public ChatSession createSession(String prompt) {
        // Clean up old sessions if needed
        if (sessions.size() >= MAX_SESSIONS) {
            cleanupOldSessions();
        }
        
        ChatSession session = ChatSession.create(prompt);
        sessions.put(session.getId(), session);
        return session;
    }
    
    /**
     * Gets a session by ID.
     * 
     * @param id The session ID
     * @return The ChatSession, or null if not found
     */
    public ChatSession getSession(String id) {
        if (id == null) {
            return null;
        }
        return sessions.get(id);
    }
    
    /**
     * Updates an existing session.
     * 
     * @param session The session to update
     */
    public void updateSession(ChatSession session) {
        if (session != null && session.getId() != null) {
            sessions.put(session.getId(), session);
        }
    }
    
    /**
     * Deletes a session by ID.
     * 
     * @param id The session ID
     * @return The deleted session, or null if not found
     */
    public ChatSession deleteSession(String id) {
        if (id == null) {
            return null;
        }
        return sessions.remove(id);
    }
    
    /**
     * Gets all sessions.
     * 
     * @return Collection of all sessions
     */
    public Collection<ChatSession> getAllSessions() {
        return sessions.values();
    }
    
    /**
     * Gets the total number of sessions.
     * 
     * @return Session count
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * Checks if a session exists.
     * 
     * @param id The session ID
     * @return true if session exists
     */
    public boolean sessionExists(String id) {
        return id != null && sessions.containsKey(id);
    }
    
    /**
     * Gets recent sessions sorted by creation time (newest first).
     * 
     * @param limit Maximum number of sessions to return
     * @return List of recent sessions
     */
    public List<ChatSession> getRecentSessions(int limit) {
        List<ChatSession> sorted = new ArrayList<>(sessions.values());
        sorted.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        
        if (sorted.size() <= limit) {
            return sorted;
        }
        return sorted.subList(0, limit);
    }
    
    /**
     * Removes sessions older than the expiry time.
     */
    public void cleanupOldSessions() {
        long expiryTime = System.currentTimeMillis() - SESSION_EXPIRY_MS;
        sessions.entrySet().removeIf(entry -> 
            entry.getValue().getCreatedAt() < expiryTime);
    }
    
    /**
     * Clears all sessions.
     */
    public void clearAllSessions() {
        sessions.clear();
    }
    
    /**
     * Gets sessions as JSON array.
     * 
     * @param limit Maximum sessions to return
     * @return JSON array string
     */
    public String toJson(int limit) {
        List<ChatSession> recentSessions = getRecentSessions(limit);
        StringBuilder json = new StringBuilder("[");
        
        boolean first = true;
        for (ChatSession session : recentSessions) {
            if (!first) {
                json.append(",");
            }
            json.append(session.toSummaryJson());
            first = false;
        }
        
        json.append("]");
        return json.toString();
    }
}
