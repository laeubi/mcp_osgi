package io.github.laeubi.mcp.osgi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Proof-of-concept HTTP server using JDK's built-in HTTP server.
 * 
 * This demonstrates how jdk.httpserver could be used as a lightweight alternative
 * to Jetty or other servlet containers for serving HTTP-based MCP requests.
 * 
 * This is NOT a complete MCP transport implementation, but rather a demonstration
 * of the JDK HTTP server capabilities and how it could be integrated with MCP.
 * 
 * Key advantages of jdk.httpserver:
 * - No external dependencies (part of JDK since Java 6)
 * - Simple API for basic HTTP needs
 * - Lightweight and easy to embed
 * - Sufficient for simple request/response patterns
 * 
 * Limitations:
 * - Not designed for high-performance production use
 * - No built-in servlet API support
 * - Limited feature set compared to Jetty/Tomcat
 * - Requires manual implementation of MCP protocol handling
 * 
 * Usage:
 * <pre>
 * JdkHttpServerDemo server = new JdkHttpServerDemo(8080, "/mcp");
 * server.start();
 * // ... server runs ...
 * server.stop();
 * </pre>
 */
public class JdkHttpServerDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(JdkHttpServerDemo.class);
    
    private final int port;
    private final String contextPath;
    private HttpServer httpServer;
    private final ObjectMapper objectMapper;
    
    /**
     * Create a new JDK HTTP Server demo.
     * 
     * @param port Port number to listen on
     * @param contextPath HTTP context path (e.g., "/mcp")
     */
    public JdkHttpServerDemo(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Start the HTTP server.
     * 
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext(contextPath, new McpDemoHandler());
        httpServer.createContext("/health", new HealthCheckHandler());
        httpServer.setExecutor(null); // Use default executor
        httpServer.start();
        logger.info("Demo HTTP Server started on port {} at context path {}", port, contextPath);
    }
    
    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("Demo HTTP Server stopped");
        }
    }
    
    /**
     * HTTP handler that demonstrates JSON-RPC request handling.
     */
    private class McpDemoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Only accept POST requests for MCP
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, createErrorResponse("Method not allowed", -32600));
                return;
            }
            
            try {
                // Read the JSON-RPC request
                InputStream requestBody = exchange.getRequestBody();
                String requestJson = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Received HTTP request: {}", requestJson);
                
                // Parse JSON-RPC request
                JsonNode request = objectMapper.readTree(requestJson);
                
                // Validate basic JSON-RPC structure
                if (!request.has("jsonrpc") || !"2.0".equals(request.get("jsonrpc").asText())) {
                    sendJsonResponse(exchange, 400, createErrorResponse("Invalid JSON-RPC version", -32600));
                    return;
                }
                
                if (!request.has("method")) {
                    sendJsonResponse(exchange, 400, createErrorResponse("Missing method", -32600));
                    return;
                }
                
                String method = request.get("method").asText();
                JsonNode id = request.get("id");
                
                // Handle demo methods
                String response;
                if (id != null && !id.isNull()) {
                    // Request requires response
                    response = handleDemoMethod(method, id);
                } else {
                    // Notification (no response needed)
                    logger.debug("Received notification: {}", method);
                    response = "";
                }
                
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                logger.error("Error processing request", e);
                sendJsonResponse(exchange, 500, createErrorResponse("Internal server error", -32603));
            }
        }
        
        private String handleDemoMethod(String method, JsonNode id) throws Exception {
            // Demonstrate handling different MCP methods
            switch (method) {
                case "initialize":
                    return createSuccessResponse(id, "{\n" +
                        "  \"protocolVersion\": \"2024-11-05\",\n" +
                        "  \"serverInfo\": {\n" +
                        "    \"name\": \"mcp-osgi-http-demo\",\n" +
                        "    \"version\": \"1.0.0\"\n" +
                        "  },\n" +
                        "  \"capabilities\": {\n" +
                        "    \"tools\": true\n" +
                        "  }\n" +
                        "}");
                
                case "tools/list":
                    return createSuccessResponse(id, "{\n" +
                        "  \"tools\": [\n" +
                        "    {\n" +
                        "      \"name\": \"hello_osgi\",\n" +
                        "      \"description\": \"Demo tool via HTTP\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");
                
                default:
                    return createErrorResponse("Method not found: " + method, -32601, id);
            }
        }
        
        private String createSuccessResponse(JsonNode id, String resultJson) {
            return String.format(
                "{\"jsonrpc\": \"2.0\", \"id\": %s, \"result\": %s}",
                id.toString(), resultJson
            );
        }
        
        private String createErrorResponse(String message, int code) {
            return createErrorResponse(message, code, null);
        }
        
        private String createErrorResponse(String message, int code, JsonNode id) {
            String idStr = (id != null && !id.isNull()) ? id.toString() : "null";
            return String.format(
                "{\"jsonrpc\": \"2.0\", \"id\": %s, \"error\": {\"code\": %d, \"message\": \"%s\"}}",
                idStr, code, message
            );
        }
        
        private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // For web clients
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
    
    /**
     * Simple health check handler.
     */
    private class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\": \"ok\", \"server\": \"jdk.httpserver\"}";
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
    
    /**
     * Main method for testing the HTTP server demo.
     */
    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
            JdkHttpServerDemo server = new JdkHttpServerDemo(port, "/mcp");
            server.start();
            
            logger.info("Demo server is running. Try:");
            logger.info("  curl http://localhost:{}/health", port);
            logger.info("  curl -X POST http://localhost:{}/mcp -H 'Content-Type: application/json' -d '{{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{{}}}}'", port);
            
            // Keep running until interrupted
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error running demo server", e);
            System.exit(1);
        }
    }
}
