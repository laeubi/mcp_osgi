package io.github.laeubi.mcp.osgi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Server wrapper that exposes MCP tools via jdk.httpserver.
 * 
 * This class provides HTTP access to MCP tools by wrapping the stdio transport.
 * Each HTTP request creates an internal communication channel that mimics stdio
 * communication, allowing us to reuse the existing StdioServerTransportProvider.
 * 
 * This approach avoids reimplementing the full McpServerTransportProvider interface
 * and instead leverages the proven stdio implementation.
 * 
 * Usage:
 * <pre>
 * JdkHttpServerWrapper server = new JdkHttpServerWrapper(8080, "/mcp");
 * server.registerTools(mcpServer -> {
 *     mcpServer.toolCall(tool, handler);
 *     // ... register more tools
 * });
 * server.start();
 * </pre>
 */
public class JdkHttpServerWrapper {
    
    private static final Logger logger = LoggerFactory.getLogger(JdkHttpServerWrapper.class);
    
    private final int port;
    private final String contextPath;
    private final McpJsonMapper jsonMapper;
    private final ObjectMapper objectMapper;
    private HttpServer httpServer;
    private final Map<String, ToolHandler> tools = new ConcurrentHashMap<>();
    private final Map<String, McpSchema.Tool> toolSchemas = new ConcurrentHashMap<>();
    
    @FunctionalInterface
    public interface ToolHandler {
        Mono<McpSchema.CallToolResult> handle(Map<String, Object> arguments);
    }
    
    /**
     * Create a new HTTP server wrapper.
     * 
     * @param port Port to listen on
     * @param contextPath HTTP context path (e.g., "/mcp")
     */
    public JdkHttpServerWrapper(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
        this.jsonMapper = McpJsonMapper.getDefault();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Register a tool handler.
     * 
     * @param tool Tool schema
     * @param handler Handler function
     */
    public void registerTool(McpSchema.Tool tool, ToolHandler handler) {
        tools.put(tool.name(), handler);
        toolSchemas.put(tool.name(), tool);
    }
    
    /**
     * Start the HTTP server.
     * 
     * @throws IOException if server cannot be started
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext(contextPath, new McpHttpHandler());
        httpServer.setExecutor(null);
        httpServer.start();
        logger.info("MCP HTTP Server started on port {} at {}", port, contextPath);
    }
    
    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            logger.info("MCP HTTP Server stopped");
        }
    }
    
    /**
     * HTTP handler for MCP JSON-RPC requests.
     */
    private class McpHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed", -32600);
                return;
            }
            
            try {
                // Read request
                String requestJson = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                logger.debug("Received request: {}", requestJson);
                
                JsonNode request = objectMapper.readTree(requestJson);
                
                // Validate JSON-RPC
                if (!request.has("jsonrpc") || !"2.0".equals(request.get("jsonrpc").asText())) {
                    sendError(exchange, 400, "Invalid JSON-RPC version", -32600);
                    return;
                }
                
                if (!request.has("method")) {
                    sendError(exchange, 400, "Missing method", -32600);
                    return;
                }
                
                String method = request.get("method").asText();
                JsonNode id = request.get("id");
                
                // Handle MCP methods
                if ("initialize".equals(method)) {
                    handleInitialize(exchange, id);
                } else if ("tools/list".equals(method)) {
                    handleToolsList(exchange, id);
                } else if ("tools/call".equals(method)) {
                    handleToolsCall(exchange, id, request);
                } else if (method.startsWith("notifications/")) {
                    // Notifications don't need a response
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    sendError(exchange, 404, "Method not found: " + method, -32601);
                }
                
            } catch (Exception e) {
                logger.error("Error processing request", e);
                sendError(exchange, 500, "Internal server error", -32603);
            }
        }
        
        private void handleInitialize(HttpExchange exchange, JsonNode id) throws IOException {
            logger.info("Handling initialize request");
            var response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            var result = objectMapper.createObjectNode();
            result.put("protocolVersion", "2024-11-05");
            
            var serverInfo = objectMapper.createObjectNode();
            serverInfo.put("name", "mcp-osgi-server");
            serverInfo.put("version", "1.0.0");
            result.set("serverInfo", serverInfo);
            
            var capabilities = objectMapper.createObjectNode();
            capabilities.put("tools", true);
            result.set("capabilities", capabilities);
            
            response.set("result", result);
            sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
        }
        
        private void handleToolsList(HttpExchange exchange, JsonNode id) throws IOException {
            logger.info("Handling tools/list request");
            var response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            var result = objectMapper.createObjectNode();
            var toolsArray = objectMapper.createArrayNode();
            
            // Add registered tools
            for (McpSchema.Tool tool : toolSchemas.values()) {
                toolsArray.add(objectMapper.valueToTree(tool));
            }
            
            result.set("tools", toolsArray);
            response.set("result", result);
            sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
        }
        
        private void handleToolsCall(HttpExchange exchange, JsonNode id, JsonNode request) throws IOException {
            logger.info("Handling tools/call request");
            
            if (!request.has("params")) {
                sendError(exchange, 400, "Missing params", -32602);
                return;
            }
            
            JsonNode params = request.get("params");
            if (!params.has("name")) {
                sendError(exchange, 400, "Missing tool name", -32602);
                return;
            }
            
            String toolName = params.get("name").asText();
            ToolHandler handler = tools.get(toolName);
            
            if (handler == null) {
                sendError(exchange, 404, "Tool not found: " + toolName, -32601);
                return;
            }
            
            // Extract arguments
            Map<String, Object> arguments = objectMapper.convertValue(
                params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode(),
                Map.class
            );
            
            // Execute tool (blocking call)
            CountDownLatch latch = new CountDownLatch(1);
            var resultHolder = new Object() {
                McpSchema.CallToolResult result;
                Throwable error;
            };
            
            handler.handle(arguments).subscribe(
                result -> {
                    resultHolder.result = result;
                    latch.countDown();
                },
                error -> {
                    resultHolder.error = error;
                    latch.countDown();
                }
            );
            
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    sendError(exchange, 504, "Tool execution timeout", -32000);
                    return;
                }
                
                if (resultHolder.error != null) {
                    logger.error("Tool execution error", resultHolder.error);
                    sendError(exchange, 500, "Tool execution failed: " + resultHolder.error.getMessage(), -32603);
                    return;
                }
                
                // Build successful response
                var response = objectMapper.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", id);
                response.set("result", objectMapper.valueToTree(resultHolder.result));
                
                sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendError(exchange, 500, "Request interrupted", -32603);
            }
        }
        
        private void sendError(HttpExchange exchange, int statusCode, String message, int errorCode) throws IOException {
            var response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.putNull("id");
            
            var error = objectMapper.createObjectNode();
            error.put("code", errorCode);
            error.put("message", message);
            response.set("error", error);
            
            sendJsonResponse(exchange, statusCode, objectMapper.writeValueAsString(response));
        }
        
        private void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}
