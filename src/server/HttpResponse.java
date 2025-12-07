package server;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Represents an HTTP response.
 * 
 * Contains all components needed to construct an HTTP response:
 * - Status code and message
 * - Headers
 * - Body content
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class HttpResponse {
    
    /** HTTP status code */
    private int statusCode;
    
    /** HTTP status message */
    private String statusMessage;
    
    /** Response headers (LinkedHashMap preserves insertion order) */
    private Map<String, String> headers;
    
    /** Response body as string */
    private String body;
    
    /** Response body as bytes (for binary content) */
    private byte[] bodyBytes;
    
    /**
     * Constructs a new HttpResponse with default 200 OK status.
     */
    public HttpResponse() {
        this.statusCode = 200;
        this.statusMessage = "OK";
        this.headers = new LinkedHashMap<>();
        this.body = "";
        this.bodyBytes = null;
        
        // Set default headers
        headers.put("Server", "ChatGPT-WebServer/1.0");
        headers.put("Connection", "close");
    }
    
    // ==================== Getters and Setters ====================
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public String getHeader(String name) {
        return headers.get(name);
    }
    
    public void setHeader(String name, String value) {
        headers.put(name, value);
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
        this.bodyBytes = null;
    }
    
    public byte[] getBodyBytes() {
        return bodyBytes;
    }
    
    public void setBodyBytes(byte[] bodyBytes) {
        this.bodyBytes = bodyBytes;
        this.body = null;
    }
    
    /**
     * Checks if the response has binary body content.
     * @return true if body is binary
     */
    public boolean hasBinaryBody() {
        return bodyBytes != null;
    }
    
    /**
     * Gets the body content as bytes.
     * If body is string, converts to bytes using UTF-8.
     * @return Body as byte array
     */
    public byte[] getBodyAsBytes() {
        if (bodyBytes != null) {
            return bodyBytes;
        }
        if (body != null) {
            return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return new byte[0];
    }
    
    /**
     * Sets a cookie in the response.
     * 
     * @param name Cookie name
     * @param value Cookie value
     * @param httpOnly Whether cookie should be HTTP only
     */
    public void setCookie(String name, String value, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=/");
        if (httpOnly) {
            cookie.append("; HttpOnly");
        }
        headers.put("Set-Cookie", cookie.toString());
    }
    
    /**
     * Sets a cookie with expiration.
     * 
     * @param name Cookie name
     * @param value Cookie value
     * @param maxAge Max age in seconds
     * @param httpOnly Whether cookie should be HTTP only
     */
    public void setCookie(String name, String value, int maxAge, boolean httpOnly) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=/");
        cookie.append("; Max-Age=").append(maxAge);
        if (httpOnly) {
            cookie.append("; HttpOnly");
        }
        headers.put("Set-Cookie", cookie.toString());
    }
    
    /**
     * Clears a cookie by setting Max-Age to 0.
     * 
     * @param name Cookie name to clear
     */
    public void clearCookie(String name) {
        headers.put("Set-Cookie", name + "=; Path=/; Max-Age=0");
    }
    
    // ==================== Factory Methods ====================
    
    /**
     * Creates a 200 OK response with text body.
     */
    public static HttpResponse ok(String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(body);
        return response;
    }
    
    /**
     * Creates a 200 OK response with HTML body.
     */
    public static HttpResponse html(String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setBody(body);
        return response;
    }
    
    /**
     * Creates a 200 OK response with JSON body.
     */
    public static HttpResponse json(String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.setHeader("Content-Type", "application/json; charset=UTF-8");
        response.setBody(body);
        return response;
    }
    
    /**
     * Creates a 201 Created response.
     */
    public static HttpResponse created(String location, String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(201);
        response.setStatusMessage("Created");
        response.setHeader("Location", location);
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(body);
        return response;
    }
    
    /**
     * Creates a 302 Found redirect response.
     */
    public static HttpResponse redirect(String location) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(302);
        response.setStatusMessage("Found");
        response.setHeader("Location", location);
        response.setBody("");
        return response;
    }
    
    /**
     * Creates a 400 Bad Request response.
     */
    public static HttpResponse badRequest(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(400);
        response.setStatusMessage("Bad Request");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
    
    /**
     * Creates a 401 Unauthorized response.
     */
    public static HttpResponse unauthorized(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(401);
        response.setStatusMessage("Unauthorized");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
    
    /**
     * Creates a 403 Forbidden response.
     */
    public static HttpResponse forbidden(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(403);
        response.setStatusMessage("Forbidden");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
    
    /**
     * Creates a 404 Not Found response.
     */
    public static HttpResponse notFound(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(404);
        response.setStatusMessage("Not Found");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
    
    /**
     * Creates a 405 Method Not Allowed response.
     */
    public static HttpResponse methodNotAllowed(String allowedMethods) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(405);
        response.setStatusMessage("Method Not Allowed");
        response.setHeader("Allow", allowedMethods);
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody("405 Method Not Allowed");
        return response;
    }
    
    /**
     * Creates a 500 Internal Server Error response.
     */
    public static HttpResponse serverError(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(500);
        response.setStatusMessage("Internal Server Error");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
    
    /**
     * Creates a 503 Service Unavailable response.
     */
    public static HttpResponse serviceUnavailable(String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(503);
        response.setStatusMessage("Service Unavailable");
        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
        response.setBody(message);
        return response;
    }
}
