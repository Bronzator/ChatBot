package util;

import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Security utility functions.
 * 
 * Provides:
 * - SHA-256 hashing for passwords
 * - Secure random token generation
 * - Path validation to prevent directory traversal
 * - Input sanitization
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class SecurityUtils {
    
    /** Secure random number generator */
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /** Characters for token generation */
    private static final String TOKEN_CHARS = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    
    /**
     * Private constructor to prevent instantiation.
     */
    private SecurityUtils() {
        // Utility class
    }
    
    /**
     * Computes SHA-256 hash of a string.
     * 
     * @param input The input string
     * @return Hex-encoded SHA-256 hash
     */
    public static String sha256(String input) {
        if (input == null) {
            return null;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param bytes The byte array
     * @return Hex string
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Generates a secure random token.
     * 
     * @param length Length of the token
     * @return Random alphanumeric token
     */
    public static String generateSecureToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(TOKEN_CHARS.length());
            sb.append(TOKEN_CHARS.charAt(index));
        }
        return sb.toString();
    }
    
    /**
     * Generates a secure random token as Base64.
     * 
     * @param byteLength Number of random bytes
     * @return Base64-encoded token
     */
    public static String generateSecureTokenBase64(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Checks if a path is safe (no directory traversal).
     * 
     * @param path The path to check
     * @return true if path is safe
     */
    public static boolean isSafePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Normalize path
        String normalized = path.replace('\\', '/');
        
        // Check for directory traversal attempts
        if (normalized.contains("..") ||
            normalized.contains("./") ||
            normalized.contains("/.") ||
            normalized.startsWith("/etc/") ||
            normalized.startsWith("/var/") ||
            normalized.contains(":") ||
            normalized.contains("~")) {
            return false;
        }
        
        // Check for null bytes
        if (path.indexOf('\0') >= 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Sanitizes a filename by removing potentially dangerous characters.
     * 
     * @param filename The filename to sanitize
     * @return Sanitized filename
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        
        // Remove path separators and dangerous characters
        return filename.replaceAll("[/\\\\:*?\"<>|\\x00]", "_");
    }
    
    /**
     * Sanitizes input to prevent XSS attacks.
     * 
     * @param input The input to sanitize
     * @return Sanitized input
     */
    public static String sanitizeHtml(String input) {
        if (input == null) {
            return null;
        }
        
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;")
                    .replace("/", "&#x2F;");
    }
    
    /**
     * Validates that a string contains only alphanumeric characters.
     * 
     * @param input The input to validate
     * @return true if alphanumeric only
     */
    public static boolean isAlphanumeric(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        for (char c : input.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Validates an email address format (basic check).
     * 
     * @param email The email to validate
     * @return true if valid format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        
        // Basic email validation
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
    
    /**
     * Generates a random 6-digit OTP code.
     * 
     * @return 6-digit OTP code
     */
    public static String generateOTP() {
        int otp = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(otp);
    }
}
