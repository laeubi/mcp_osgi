package io.github.laeubi.mcp.osgi;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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
 * The server supports three modes:
 * - stdio mode (default): Communicates via JSON-RPC 2.0 over stdio transport
 * - server mode: Runs an HTTP server with Jetty and SSE (Server-Sent Events) transport
 * - jdkserver mode: Runs an HTTP server using JDK's built-in HTTP server with SSE transport
 */
public class OsgiMcpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(OsgiMcpServer.class);
    private static final int DEFAULT_PORT = 3000;
    
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
     * Create the bundle_info tool definition.
     */
    private static McpSchema.Tool createBundleInfoTool() {
        Map<String, Object> fileProperty = Map.of(
            "type", "string",
            "description", "Path to a JAR file or MANIFEST.MF file to analyze"
        );
        
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("file", fileProperty),
            List.of("file"),
            null,
            null,
            null
        );
        
        return McpSchema.Tool.builder()
            .name("bundle_info")
            .description("Analyzes a JAR or MANIFEST.MF file to determine if it's an OSGi bundle and returns bundle metadata including symbolic name, version, required bundles, required packages, and required capabilities")
            .inputSchema(inputSchema)
            .build();
    }
    
    /**
     * Handle the bundle_info tool call.
     */
    private static Mono<McpSchema.CallToolResult> handleBundleInfo(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            String file = arguments.getOrDefault("file", "").toString();
            
            StringBuilder response = new StringBuilder();
            response.append("=== Bundle Information Analysis ===\n\n");
            response.append("File: ").append(file).append("\n\n");
            
            // Example content: simulate analyzing a bundle
            if (file.endsWith(".jar") || file.endsWith("MANIFEST.MF")) {
                response.append("Status: Valid OSGi Bundle\n\n");
                response.append("Bundle-SymbolicName: org.example.mybundle\n");
                response.append("Bundle-Version: 1.2.3\n\n");
                
                response.append("Required Bundles:\n");
                response.append("  - org.eclipse.osgi.services;bundle-version=\"3.10.0\"\n");
                response.append("  - org.apache.commons.lang3;bundle-version=\"3.12.0\"\n\n");
                
                response.append("Required Packages:\n");
                response.append("  - javax.servlet;version=\"[3.1.0,4.0.0)\"\n");
                response.append("  - org.osgi.framework;version=\"1.10.0\"\n");
                response.append("  - com.google.gson;version=\"[2.8.0,3.0.0)\"\n\n");
                
                response.append("Required Capabilities:\n");
                response.append("  - osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=11))\"\n");
                response.append("  - osgi.service;filter:=\"(objectClass=org.osgi.service.log.LogService)\"\n");
            } else {
                response.append("Status: Not a bundle\n");
                response.append("Reason: File does not appear to be a JAR or MANIFEST.MF file\n");
            }
            
            response.append("\n");
            response.append("Note: This is example content. In a real implementation, this tool would:\n");
            response.append("- Parse the MANIFEST.MF from the JAR or read the manifest file directly\n");
            response.append("- Extract Bundle-SymbolicName and Bundle-Version headers\n");
            response.append("- Parse Require-Bundle header for bundle dependencies\n");
            response.append("- Parse Import-Package header for package dependencies\n");
            response.append("- Parse Require-Capability header for capability requirements\n");
            
            McpSchema.TextContent textContent = new McpSchema.TextContent(response.toString());
            return new McpSchema.CallToolResult(List.of(textContent), false);
        });
    }
    
    /**
     * Create the find tool definition.
     */
    private static McpSchema.Tool createFindTool() {
        Map<String, Object> typeProperty = Map.of(
            "type", "string",
            "description", "Type of search: 'package', 'bundle', or 'capability'",
            "enum", List.of("package", "bundle", "capability")
        );
        
        Map<String, Object> nameProperty = Map.of(
            "type", "string",
            "description", "Name of the package, bundle, or capability to find"
        );
        
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object",
            Map.of("type", typeProperty, "name", nameProperty),
            List.of("type", "name"),
            null,
            null,
            null
        );
        
        return McpSchema.Tool.builder()
            .name("find")
            .description("Searches for OSGi packages, bundles, or capabilities and returns download URLs, bundle names, and versions where they can be found")
            .inputSchema(inputSchema)
            .build();
    }
    
    /**
     * Handle the find tool call.
     */
    private static Mono<McpSchema.CallToolResult> handleFind(Map<String, Object> arguments) {
        return Mono.fromSupplier(() -> {
            String type = arguments.getOrDefault("type", "package").toString();
            String name = arguments.getOrDefault("name", "").toString();
            
            StringBuilder response = new StringBuilder();
            response.append("=== OSGi Repository Search ===\n\n");
            response.append("Search Type: ").append(type).append("\n");
            response.append("Search Name: ").append(name).append("\n\n");
            response.append("Results:\n\n");
            
            // Example content based on search type
            switch (type) {
                case "package":
                    response.append("1. Bundle: org.apache.commons.logging\n");
                    response.append("   Version: 1.2.0\n");
                    response.append("   Exports Package: ").append(name).append("\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar\n\n");
                    
                    response.append("2. Bundle: org.slf4j.impl.commons.logging\n");
                    response.append("   Version: 1.7.36\n");
                    response.append("   Exports Package: ").append(name).append("\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/org/slf4j/slf4j-jcl/1.7.36/slf4j-jcl-1.7.36.jar\n");
                    break;
                    
                case "bundle":
                    response.append("1. Bundle: ").append(name).append("\n");
                    response.append("   Version: 3.10.2\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.osgi/3.10.2/org.eclipse.osgi-3.10.2.jar\n\n");
                    
                    response.append("2. Bundle: ").append(name).append("\n");
                    response.append("   Version: 3.15.0\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.osgi/3.15.0/org.eclipse.osgi-3.15.0.jar\n");
                    break;
                    
                case "capability":
                    response.append("1. Bundle: org.apache.felix.scr\n");
                    response.append("   Version: 2.2.0\n");
                    response.append("   Provides Capability: ").append(name).append("\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/org/apache/felix/org.apache.felix.scr/2.2.0/org.apache.felix.scr-2.2.0.jar\n\n");
                    
                    response.append("2. Bundle: org.eclipse.equinox.ds\n");
                    response.append("   Version: 1.6.0\n");
                    response.append("   Provides Capability: ").append(name).append("\n");
                    response.append("   Download URL: https://repo1.maven.org/maven2/org/eclipse/platform/org.eclipse.equinox.ds/1.6.0/org.eclipse.equinox.ds-1.6.0.jar\n");
                    break;
            }
            
            response.append("\n");
            response.append("Note: This is example content. In a real implementation, this tool would:\n");
            response.append("- Query OSGi repository indexes (OBR, p2, Maven Central)\n");
            response.append("- Search for exact matches and similar alternatives\n");
            response.append("- Return actual download URLs with version information\n");
            response.append("- Provide dependency information for each result\n");
            
            McpSchema.TextContent textContent = new McpSchema.TextContent(response.toString());
            return new McpSchema.CallToolResult(List.of(textContent), false);
        });
    }
    
    /**
     * Build the MCP server with all tools configured.
     */
    private static McpServer.AsyncSpecification<?> buildServer(
            io.modelcontextprotocol.spec.McpServerTransportProvider transportProvider) {
        return McpServer.async(transportProvider)
            .serverInfo("mcp-osgi-server", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .toolCall(createHelloOsgiTool(), (exchange, request) -> 
                handleHelloOsgi(request.arguments()))
            .toolCall(createBundleInfoTool(), (exchange, request) -> 
                handleBundleInfo(request.arguments()))
            .toolCall(createFindTool(), (exchange, request) -> 
                handleFind(request.arguments()));
    }
    
    /**
     * Start the server in stdio mode.
     */
    private static void startStdioMode() throws InterruptedException {
        logger.info("Starting MCP OSGi Server in stdio mode...");
        
        // Create JSON mapper
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        
        // Create stdio transport provider
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);
        
        // Build and start the MCP server
        var server = buildServer(transportProvider).build();
        
        logger.info("MCP OSGi Server started successfully in stdio mode");
        
        // Keep the server running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Server interrupted, shutting down...");
            server.closeGracefully().block();
            throw e;
        }
    }
    
    /**
     * Start the server in HTTP server mode with SSE transport using Jetty.
     */
    private static void startServerMode(int port) throws Exception {
        logger.info("Starting MCP OSGi Server in server mode (Jetty) on port {}...", port);
        
        // Create JSON mapper
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        
        // Create HTTP SSE transport provider (which is also a servlet)
        HttpServletSseServerTransportProvider transportProvider = 
            HttpServletSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .baseUrl("http://localhost:" + port)
                .messageEndpoint("/mcp/message")
                .sseEndpoint("/mcp/sse")
                .build();
        
        // Build the MCP server - this will automatically set the session factory
        var mcpServer = buildServer(transportProvider).build();
        
        // Create Jetty server
        Server jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);
        
        // Add the transport provider as a servlet - it extends HttpServlet
        context.addServlet(new ServletHolder(transportProvider), "/mcp/*");
        
        // Start the Jetty server
        try {
            jettyServer.start();
            logger.info("MCP OSGi Server started successfully on http://localhost:{}/mcp", port);
            jettyServer.join();
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            throw e;
        } finally {
            mcpServer.closeGracefully().block();
        }
    }
    
    /**
     * Start the server in HTTP server mode with SSE transport using JDK's built-in HTTP server.
     */
    private static void startJdkServerMode(int port) throws Exception {
        logger.info("Starting MCP OSGi Server in jdkserver mode on port {}...", port);
        
        // Create JSON mapper
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        
        // Create HTTP SSE transport provider (which is also a servlet)
        HttpServletSseServerTransportProvider transportProvider = 
            HttpServletSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .baseUrl("http://localhost:" + port)
                .messageEndpoint("/mcp/message")
                .sseEndpoint("/mcp/sse")
                .build();
        
        // Build the MCP server - this will automatically set the session factory
        var mcpServer = buildServer(transportProvider).build();
        
        // Create JDK HTTP server wrapper
        JdkHttpServerWrapper jdkServer = new JdkHttpServerWrapper(port, transportProvider);
        
        // Start the JDK HTTP server
        try {
            jdkServer.start();
            logger.info("MCP OSGi Server started successfully on http://localhost:{}/mcp", port);
            jdkServer.join();
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            throw e;
        } finally {
            mcpServer.closeGracefully().block();
            jdkServer.stop();
        }
    }
    
    /**
     * Main entry point.
     * 
     * Usage:
     *   java -jar mcp-osgi-server.jar                 # Start in stdio mode (default)
     *   java -jar mcp-osgi-server.jar server          # Start in server mode (Jetty) on port 3000
     *   java -jar mcp-osgi-server.jar server 8080     # Start in server mode (Jetty) on port 8080
     *   java -jar mcp-osgi-server.jar jdkserver       # Start in jdkserver mode on port 3000
     *   java -jar mcp-osgi-server.jar jdkserver 8080  # Start in jdkserver mode on port 8080
     */
    public static void main(String[] args) {
        try {
            if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
                // Server mode with Jetty
                int port = DEFAULT_PORT;
                if (args.length > 1) {
                    try {
                        port = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid port number: {}", args[1]);
                        System.err.println("Usage: java -jar mcp-osgi-server.jar server [port]");
                        System.exit(1);
                    }
                }
                startServerMode(port);
            } else if (args.length > 0 && "jdkserver".equalsIgnoreCase(args[0])) {
                // JDK server mode
                int port = DEFAULT_PORT;
                if (args.length > 1) {
                    try {
                        port = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid port number: {}", args[1]);
                        System.err.println("Usage: java -jar mcp-osgi-server.jar jdkserver [port]");
                        System.exit(1);
                    }
                }
                startJdkServerMode(port);
            } else {
                // Stdio mode (default)
                startStdioMode();
            }
        } catch (Exception e) {
            logger.error("Server failed", e);
            System.exit(1);
        }
    }
}
