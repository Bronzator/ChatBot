package server;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP request.
 * 
 * Contains all parsed components of an HTTP request including:
 * - Method (GET, POST, HEAD)
 * - Path (URI)
 * - HTTP version
 * - Headers
 * - Body content
 * - Query parameters
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class HttpRequest {
    
    /** HTTP method (GET, POST, HEAD) */
    private String method;
    
    /** Request path/URI */
    private String path;
    
    /** HTTP version (e.g., HTTP/1.1) */
    private String httpVersion;
    
    /** Request headers */
    private Map<String, String> headers;
    
    /** Request body content */
    private String body;
    
    /** Query parameters from URL */
    private Map<String, String> queryParams;
    
    /** Cookies from request */
    private Map<String, String> cookies;
    
    /**
     * Constructs a new empty HttpRequest.
     */
    public HttpRequest() {
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
        this.cookies = new HashMap<>();
        this.body = "";
    }
    
    // ==================== Getters and Setters ====================
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getHttpVersion() {
        return httpVersion;
    }
    
    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }
    
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public Map<String, String> getQueryParams() {
        return queryParams;
    }
    
    public String getQueryParam(String name) {
        return queryParams.get(name);
    }
    
    public void setQueryParam(String name, String value) {
        queryParams.put(name, value);
    }
    
    public Map<String, String> getCookies() {
        return cookies;
    }
    
    public String getCookie(String name) {
        return cookies.get(name);
    }
    
    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }
    
    /**
     * Gets the Content-Type header value.
     * @return Content-Type or null if not set
     */
    public String getContentType() {
        return getHeader("content-type");
    }
    
    /**
     * Gets the Content-Length header value.
     * @return Content-Length as int, or 0 if not set
     */
    public int getContentLength() {
        String length = getHeader("content-length");
        if (length != null) {
            try {
                return Integer.parseInt(length.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s", method, path, httpVersion);
    }
}
