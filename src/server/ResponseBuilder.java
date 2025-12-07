package server;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Builds HTTP responses from HttpResponse objects.
 * 
 * This class is responsible for:
 * - Constructing valid HTTP response format
 * - Setting required headers
 * - Handling HEAD requests (no body)
 * - Formatting dates in HTTP format
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ResponseBuilder {
    
    /** HTTP date format (RFC 1123) */
    private static final SimpleDateFormat HTTP_DATE_FORMAT;
    
    static {
        HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    /**
     * Builds a complete HTTP response as bytes.
     * 
     * @param response The HttpResponse object
     * @param method The request method (used to omit body for HEAD)
     * @return Complete HTTP response as byte array
     */
    public byte[] build(HttpResponse response, String method) {
        StringBuilder builder = new StringBuilder();
        
        // Status line
        builder.append("HTTP/1.1 ")
               .append(response.getStatusCode())
               .append(" ")
               .append(response.getStatusMessage())
               .append("\r\n");
        
        // Get body bytes
        byte[] bodyBytes = response.getBodyAsBytes();
        
        // Add Date header
        builder.append("Date: ").append(getHttpDate()).append("\r\n");
        
        // Add Content-Length header
        builder.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        
        // Add other headers
        for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            // Skip Content-Length as we already added it
            if (!header.getKey().equalsIgnoreCase("Content-Length")) {
                builder.append(header.getKey())
                       .append(": ")
                       .append(header.getValue())
                       .append("\r\n");
            }
        }
        
        // End headers
        builder.append("\r\n");
        
        // Convert headers to bytes
        byte[] headerBytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        
        // For HEAD requests, don't include body
        if ("HEAD".equalsIgnoreCase(method)) {
            return headerBytes;
        }
        
        // Combine headers and body
        byte[] result = new byte[headerBytes.length + bodyBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, result, headerBytes.length, bodyBytes.length);
        
        return result;
    }
    
    /**
     * Gets the current date in HTTP format.
     * 
     * @return Formatted date string
     */
    public static String getHttpDate() {
        synchronized (HTTP_DATE_FORMAT) {
            return HTTP_DATE_FORMAT.format(new Date());
        }
    }
    
    /**
     * Formats a timestamp as HTTP date.
     * 
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date string
     */
    public static String getHttpDate(long timestamp) {
        synchronized (HTTP_DATE_FORMAT) {
            return HTTP_DATE_FORMAT.format(new Date(timestamp));
        }
    }
}
