package io.github.laeubi.mcp.osgi;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP Server implementation for OSGi tools.
 * 
 * This server implements the Model Context Protocol (MCP) using the official
 * MCP Java SDK to expose OSGi-related tools that can be used by AI agents 
 * like GitHub Copilot.
 * 
 * The server communicates via JSON-RPC 2.0 over stdio transport.
 */
public class OsgiMcpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(OsgiMcpServer.class);
    
    /**
     * Create the hello_osgi tool definition.
     */
    private static McpSchema.Tool createHelloOsgiTool() {
        // Define input schema for the tool
        Map<String, Object> nameProperty = Map.of(
            "type", "string",
            "description", "The name to greet"
        );
        
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("name", nameProperty),
            List.of("name"),
            null,
            null,
            null
        );
        
        return McpSchema.Tool.builder()
            .name("hello_osgi")
            .description("A demonstration tool that returns a greeting with OSGi context information")
            .inputSchema(inputSchema)
            .build();
    }
    
    /**
     * Handle the hello_osgi tool call.
     */
    private static Mono<McpSchema.CallToolResult> handleHelloOsgi(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            String name = arguments.getOrDefault("name", "World").toString();
            
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
            
            // Create text content
            McpSchema.TextContent textContent = new McpSchema.TextContent(response.toString());
            
            // Return the result with content
            return new McpSchema.CallToolResult(List.of(textContent), false);
        });
    }
    
    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        logger.info("Starting MCP OSGi Server using official MCP Java SDK...");
        
        // Create JSON mapper
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        
        // Create stdio transport provider
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);
        
        // Build and configure the MCP server
        var server = McpServer.async(transportProvider)
            .serverInfo("mcp-osgi-server", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .toolCall(createHelloOsgiTool(), (exchange, request) -> 
                handleHelloOsgi(request.arguments()))
            .build();
        
        logger.info("MCP OSGi Server started successfully");
        
        // Keep the server running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Server interrupted, shutting down...");
            server.closeGracefully().block();
        }
    }
}
