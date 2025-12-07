package server;

import java.io.*;
import java.net.*;

import logging.ServerLogger;

/**
 * Handles individual client connections.
 * 
 * This class is responsible for:
 * - Reading HTTP requests from the client socket
 * - Parsing requests using RequestParser
 * - Routing requests to appropriate handlers via RequestRouter
 * - Sending responses back to the client
 * 
 * Each instance runs in its own thread from the server's thread pool.
 * 
 * @author ChatBot Project Team
 * @version 1.0
 */
public class ClientHandler implements Runnable {
    
    /** Client socket connection */
    private final Socket clientSocket;
    
    /** Reference to the parent server */
    private final WebServer server;
    
    /** Logger instance */
    private final ServerLogger logger;
    
    /** Request router for dispatching requests */
    private final RequestRouter router;
    
    /** Client IP address */
    private final String clientIP;
    
    /**
     * Constructs a new ClientHandler.
     * 
     * @param clientSocket The client's socket connection
     * @param server Reference to the WebServer instance
     */
    public ClientHandler(Socket clientSocket, WebServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.logger = ServerLogger.getInstance();
        this.router = new RequestRouter(server);
        this.clientIP = clientSocket.getInetAddress().getHostAddress();
    }
    
    /**
     * Main execution method for handling the client request.
     * 
     * Reads the request, parses it, routes it to the appropriate handler,
     * and sends back the response.
     */
    @Override
    public void run() {
        try (
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream()
        ) {
            // Set socket timeout
            clientSocket.setSoTimeout(30000); // 30 second timeout
            
            // Parse the incoming request
            RequestParser parser = new RequestParser();
            HttpRequest request = parser.parse(input);
            
            if (request == null) {
                // Invalid or empty request
                sendBadRequest(output);
                return;
            }
            
            // Log the incoming request
            logger.logRequest(clientIP, request.getMethod(), request.getPath());
            
            // Route the request and get response
            HttpResponse response = router.route(request, clientIP);
            
            // Send the response
            sendResponse(output, response, request.getMethod());
            
            // Log the response
            logger.logResponse(clientIP, request.getMethod(), request.getPath(), 
                             response.getStatusCode());
            
        } catch (SocketTimeoutException e) {
            logger.logError("Client timeout: " + clientIP);
        } catch (IOException e) {
            logger.logError("IO error handling client " + clientIP + ": " + e.getMessage());
        } catch (Exception e) {
            logger.logError("Error handling client " + clientIP + ": " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    
    /**
     * Sends an HTTP response to the client.
     * 
     * @param output The output stream to write to
     * @param response The HTTP response to send
     * @param method The request method (used to omit body for HEAD)
     * @throws IOException If writing fails
     */
    private void sendResponse(OutputStream output, HttpResponse response, String method) 
            throws IOException {
        ResponseBuilder builder = new ResponseBuilder();
        byte[] responseBytes = builder.build(response, method);
        output.write(responseBytes);
        output.flush();
    }
    
    /**
     * Sends a 400 Bad Request response.
     * 
     * @param output The output stream to write to
     * @throws IOException If writing fails
     */
    private void sendBadRequest(OutputStream output) throws IOException {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(400);
        response.setStatusMessage("Bad Request");
        response.setHeader("Content-Type", "text/plain");
        response.setBody("400 Bad Request");
        
        ResponseBuilder builder = new ResponseBuilder();
        byte[] responseBytes = builder.build(response, "GET");
        output.write(responseBytes);
        output.flush();
        
        logger.logResponse(clientIP, "UNKNOWN", "/", 400);
    }
    
    /**
     * Closes the client connection and notifies the server.
     */
    private void closeConnection() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.logError("Error closing socket: " + e.getMessage());
        }
        server.connectionClosed();
    }
}
