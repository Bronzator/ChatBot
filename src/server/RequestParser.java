package server;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Parses raw HTTP requests from input streams.
 * 
 * This class is responsible for:
 * - Reading the request line (method, path, version)
 * - Parsing headers
 * - Reading the request body (for POST requests)
 * - Parsing query parameters from URL
 * - Parsing cookies from headers
 * 
 * Implements a custom HTTP parser without using external libraries.
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class RequestParser {
    
    /** Maximum header size in bytes */
    private static final int MAX_HEADER_SIZE = 8192;
    
    /** Maximum body size in bytes (10MB) */
    private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;
    
    /**
     * Parses an HTTP request from the input stream.
     * 
     * @param input The input stream to read from
     * @return Parsed HttpRequest, or null if invalid
     * @throws IOException If reading fails
     */
    public HttpRequest parse(InputStream input) throws IOException {
        BufferedInputStream bufferedInput = new BufferedInputStream(input);
        
        // Read the raw header bytes
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        int b;
        int lastFour = 0;
        int bytesRead = 0;
        
        // Read until we find \r\n\r\n (end of headers)
        while ((b = bufferedInput.read()) != -1 && bytesRead < MAX_HEADER_SIZE) {
            headerBuffer.write(b);
            bytesRead++;
            
            // Track last 4 bytes to detect \r\n\r\n
            lastFour = ((lastFour << 8) | (b & 0xFF));
            if (lastFour == 0x0D0A0D0A) {
                break;
            }
        }
        
        if (bytesRead == 0) {
            return null;
        }
        
        String headerSection = headerBuffer.toString(StandardCharsets.UTF_8.name());
        String[] lines = headerSection.split("\r\n");
        
        if (lines.length == 0) {
            return null;
        }
        
        HttpRequest request = new HttpRequest();
        
        // Parse request line
        if (!parseRequestLine(lines[0], request)) {
            return null;
        }
        
        // Parse headers
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                break;
            }
            parseHeader(line, request);
        }
        
        // Parse cookies
        parseCookies(request);
        
        // Parse query parameters
        parseQueryParams(request);
        
        // Read body if Content-Length is specified
        int contentLength = request.getContentLength();
        if (contentLength > 0 && contentLength <= MAX_BODY_SIZE) {
            byte[] bodyBytes = new byte[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = bufferedInput.read(bodyBytes, totalRead, contentLength - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            request.setBody(new String(bodyBytes, 0, totalRead, StandardCharsets.UTF_8));
        }
        
        return request;
    }
    
    /**
     * Parses the HTTP request line.
     * 
     * @param line The request line (e.g., "GET /path HTTP/1.1")
     * @param request The request object to populate
     * @return true if parsing succeeded
     */
    private boolean parseRequestLine(String line, HttpRequest request) {
        String[] parts = line.split(" ");
        
        if (parts.length < 2) {
            return false;
        }
        
        String method = parts[0].toUpperCase();
        String path = parts[1];
        String httpVersion = parts.length > 2 ? parts[2] : "HTTP/1.0";
        
        // Validate method
        if (!isValidMethod(method)) {
            return false;
        }
        
        request.setMethod(method);
        request.setPath(path);
        request.setHttpVersion(httpVersion);
        
        return true;
    }
    
    /**
     * Checks if the HTTP method is valid.
     * 
     * @param method The HTTP method
     * @return true if valid
     */
    private boolean isValidMethod(String method) {
        return method.equals("GET") || 
               method.equals("POST") || 
               method.equals("HEAD") ||
               method.equals("PUT") ||
               method.equals("DELETE") ||
               method.equals("OPTIONS");
    }
    
    /**
     * Parses a single header line.
     * 
     * @param line The header line (e.g., "Content-Type: application/json")
     * @param request The request object to populate
     */
    private void parseHeader(String line, HttpRequest request) {
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            request.setHeader(name, value);
        }
    }
    
    /**
     * Parses cookies from the Cookie header.
     * 
     * @param request The request object containing headers
     */
    private void parseCookies(HttpRequest request) {
        String cookieHeader = request.getHeader("cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    request.setCookie(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }
    
    /**
     * Parses query parameters from the URL path.
     * 
     * @param request The request object containing the path
     */
    private void parseQueryParams(HttpRequest request) {
        String path = request.getPath();
        int queryIndex = path.indexOf('?');
        
        if (queryIndex > 0) {
            // Extract path without query string
            String queryString = path.substring(queryIndex + 1);
            request.setPath(path.substring(0, queryIndex));
            
            // Parse query parameters
            String[] params = queryString.split("&");
            for (String param : params) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    try {
                        String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name());
                        String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
                        request.setQueryParam(name, value);
                    } catch (UnsupportedEncodingException e) {
                        // Should never happen with UTF-8
                    }
                } else if (parts.length == 1) {
                    try {
                        String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name());
                        request.setQueryParam(name, "");
                    } catch (UnsupportedEncodingException e) {
                        // Should never happen with UTF-8
                    }
                }
            }
        }
    }
    
    /**
     * Parses form data from POST body (application/x-www-form-urlencoded).
     * 
     * @param body The request body
     * @return Map of form parameters
     */
    public static java.util.Map<String, String> parseFormData(String body) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        
        if (body == null || body.isEmpty()) {
            return params;
        }
        
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                try {
                    String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
                    params.put(name, value);
                } catch (UnsupportedEncodingException e) {
                    // Should never happen with UTF-8
                }
            }
        }
        
        return params;
    }
    
    /**
     * Parses JSON from request body (simple key-value extraction).
     * 
     * @param body The JSON body
     * @param key The key to extract
     * @return The value, or null if not found
     */
    public static String parseJsonValue(String body, String key) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        // Simple JSON parsing for key: "value" or key: value
        String searchKey = "\"" + key + "\"";
        int keyIndex = body.indexOf(searchKey);
        
        if (keyIndex == -1) {
            return null;
        }
        
        int colonIndex = body.indexOf(':', keyIndex);
        if (colonIndex == -1) {
            return null;
        }
        
        // Find value start
        int valueStart = colonIndex + 1;
        while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= body.length()) {
            return null;
        }
        
        // Check if value is quoted
        if (body.charAt(valueStart) == '"') {
            int valueEnd = body.indexOf('"', valueStart + 1);
            if (valueEnd > valueStart) {
                return body.substring(valueStart + 1, valueEnd);
            }
        } else {
            // Unquoted value (number, boolean, etc.)
            int valueEnd = valueStart;
            while (valueEnd < body.length()) {
                char c = body.charAt(valueEnd);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                valueEnd++;
            }
            return body.substring(valueStart, valueEnd);
        }
        
        return null;
    }
}
