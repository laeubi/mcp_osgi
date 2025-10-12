package io.github.laeubi.mcp.osgi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Server implementation for OSGi tools.
 * 
 * This server implements the Model Context Protocol (MCP) to expose OSGi-related
 * tools that can be used by AI agents like GitHub Copilot.
 * 
 * The server communicates via JSON-RPC 2.0 over stdio transport.
 */
public class OsgiMcpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(OsgiMcpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();
    private final BufferedReader reader;
    private final PrintWriter writer;
    
    public OsgiMcpServer() {
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        registerTools();
    }
    
    /**
     * Register all available tools with their handlers.
     */
    private void registerTools() {
        // Register the hello_osgi tool
        toolHandlers.put("hello_osgi", new HelloOsgiToolHandler());
    }
    
    /**
     * Start the MCP server and process requests.
     */
    public void start() {
        logger.info("Starting MCP OSGi Server...");
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    JsonNode request = objectMapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    
                    if (response != null) {
                        writer.println(objectMapper.writeValueAsString(response));
                        writer.flush();
                    }
                } catch (Exception e) {
                    logger.error("Error processing request: {}", line, e);
                    sendErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading from stdin", e);
        }
        
        logger.info("MCP OSGi Server stopped.");
    }
    
    /**
     * Handle a JSON-RPC request.
     */
    private JsonNode handleRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        JsonNode id = request.path("id");
        
        logger.debug("Handling request - method: {}, id: {}", method, id);
        
        switch (method) {
            case "initialize":
                return handleInitialize(id, params);
            case "tools/list":
                return handleToolsList(id);
            case "tools/call":
                return handleToolCall(id, params);
            case "ping":
                return handlePing(id);
            default:
                return createErrorResponse(id, -32601, "Method not found: " + method);
        }
    }
    
    /**
     * Handle the initialize request.
     */
    private JsonNode handleInitialize(JsonNode id, JsonNode params) {
        logger.info("Received initialize request");
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverName", "mcp-osgi-server");
        result.put("serverVersion", "1.0.0");
        
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode toolsCapability = objectMapper.createObjectNode();
        toolsCapability.put("listChanged", false);
        capabilities.set("tools", toolsCapability);
        result.set("capabilities", capabilities);
        
        return createSuccessResponse(id, result);
    }
    
    /**
     * Handle the tools/list request.
     */
    private JsonNode handleToolsList(JsonNode id) {
        logger.info("Received tools/list request");
        
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = objectMapper.createArrayNode();
        
        // Add hello_osgi tool
        ObjectNode helloTool = objectMapper.createObjectNode();
        helloTool.put("name", "hello_osgi");
        helloTool.put("description", "A demonstration tool that returns a greeting with OSGi context information");
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode nameProperty = objectMapper.createObjectNode();
        nameProperty.put("type", "string");
        nameProperty.put("description", "The name to greet");
        properties.set("name", nameProperty);
        
        inputSchema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("name");
        inputSchema.set("required", required);
        
        helloTool.set("inputSchema", inputSchema);
        tools.add(helloTool);
        
        result.set("tools", tools);
        return createSuccessResponse(id, result);
    }
    
    /**
     * Handle the tools/call request.
     */
    private JsonNode handleToolCall(JsonNode id, JsonNode params) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        
        logger.info("Received tools/call request for tool: {}", toolName);
        
        ToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            return createErrorResponse(id, -32602, "Unknown tool: " + toolName);
        }
        
        try {
            String result = handler.handle(arguments);
            
            ObjectNode resultNode = objectMapper.createObjectNode();
            ArrayNode content = objectMapper.createArrayNode();
            
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", result);
            content.add(textContent);
            
            resultNode.set("content", content);
            
            return createSuccessResponse(id, resultNode);
        } catch (Exception e) {
            logger.error("Error executing tool: {}", toolName, e);
            return createErrorResponse(id, -32603, "Tool execution error: " + e.getMessage());
        }
    }
    
    /**
     * Handle the ping request.
     */
    private JsonNode handlePing(JsonNode id) {
        logger.debug("Received ping request");
        ObjectNode result = objectMapper.createObjectNode();
        return createSuccessResponse(id, result);
    }
    
    /**
     * Create a success response.
     */
    private JsonNode createSuccessResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }
    
    /**
     * Create an error response.
     */
    private JsonNode createErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        
        return response;
    }
    
    /**
     * Send an error response to the client.
     */
    private void sendErrorResponse(JsonNode id, int code, String message) {
        try {
            JsonNode response = createErrorResponse(id, code, message);
            writer.println(objectMapper.writeValueAsString(response));
            writer.flush();
        } catch (Exception e) {
            logger.error("Error sending error response", e);
        }
    }
    
    /**
     * Interface for tool handlers.
     */
    interface ToolHandler {
        String handle(JsonNode arguments) throws Exception;
    }
    
    /**
     * Handler for the hello_osgi tool.
     */
    static class HelloOsgiToolHandler implements ToolHandler {
        @Override
        public String handle(JsonNode arguments) {
            String name = arguments.path("name").asText("World");
            
            // Gather OSGi-like context information
            StringBuilder response = new StringBuilder();
            response.append("Hello, ").append(name).append("!\n\n");
            response.append("=== OSGi Context Information ===\n");
            response.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
            response.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
            response.append("OS Name: ").append(System.getProperty("os.name")).append("\n");
            response.append("OS Architecture: ").append(System.getProperty("os.arch")).append("\n");
            response.append("\n");
            response.append("This is a demonstration MCP tool for OSGi contexts.\n");
            response.append("In a real OSGi environment, this tool would provide:\n");
            response.append("- Bundle information and lifecycle states\n");
            response.append("- Service registry details\n");
            response.append("- Package wiring and dependencies\n");
            response.append("- Framework diagnostics and configuration\n");
            
            return response.toString();
        }
    }
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        OsgiMcpServer server = new OsgiMcpServer();
        server.start();
    }
}
