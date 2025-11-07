# Investigation: JDK HTTP Server for MCP OSGi Server

## Executive Summary

This document summarizes the investigation into using `jdk.httpserver` as an alternative transport mechanism for the MCP OSGi Server.

## Current Implementation

The MCP OSGi Server currently uses:
- **Transport**: `StdioServerTransportProvider` (communicates via standard input/output)
- **Protocol**: JSON-RPC 2.0 over stdio
- **MCP SDK Version**: 0.14.1 (official MCP Java SDK)

**No Jetty dependency exists in the current codebase.**

## Available Transport Options in MCP SDK 0.14.1

The MCP Java SDK (v0.14.1) provides the following transport providers:

### Server-Side Transports
1. **StdioServerTransportProvider** (currently used)
   - Communication via stdin/stdout
   - Best for command-line tools and process-based integration
   - Used by GitHub Copilot and similar AI coding agents

2. **HttpServletStreamableServerTransportProvider**
   - Requires a servlet container (Jetty, Tomcat, etc.)
   - Provides HTTP-based communication with streaming support

3. **HttpServletSseServerTransportProvider** 
   - Requires a servlet container
   - Provides Server-Sent Events (SSE) for real-time updates

4. **HttpServletStatelessServerTransport**
   - Requires a servlet container
   - Provides stateless HTTP request/response

### Client-Side Transports
- `HttpClientSseClientTransport` - For SSE connections
- `HttpClientStreamableHttpTransport` - For streaming HTTP
- `StdioClientTransport` - For stdio communication

## JDK HTTP Server (jdk.httpserver)

### Overview
Java provides a built-in HTTP server in the `jdk.httpserver` module since Java 6:
- Package: `com.sun.net.httpserver`
- Module: `jdk.httpserver`
- Documentation: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/module-summary.html

### Key Features
- **Lightweight**: No external dependencies required
- **Simple API**: Easy to create HTTP endpoints
- **Sufficient for basic HTTP needs**: Request handling, response generation
- **Not production-grade**: Designed for simple use cases, not high-performance web servers

### Basic Example
```java
HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
server.createContext("/mcp", exchange -> {
    // Handle MCP requests
    String response = processRequest(exchange.getRequestBody());
    exchange.sendResponseHeaders(200, response.length());
    exchange.getResponseBody().write(response.getBytes());
    exchange.getResponseBody().close();
});
server.setExecutor(null); // Uses default executor
server.start();
```

## Analysis: Using jdk.httpserver with MCP

### Option 1: Keep Stdio Only (Current Approach)
**Pros:**
- Simple, no additional complexity
- Works perfectly with GitHub Copilot and CLI-based MCP clients
- No network exposure or security concerns
- Lightweight and fast

**Cons:**
- Limited to process-based communication
- Cannot be accessed remotely over HTTP
- Not suitable for web-based MCP clients

### Option 2: Add HTTP Support Using jdk.httpserver
**Pros:**
- No external dependencies (Jetty, Tomcat)
- Enables remote access and web-based clients
- Simpler than servlet containers
- Can run alongside stdio transport

**Cons:**
- Requires creating a custom MCP transport adapter
- MCP SDK doesn't provide built-in jdk.httpserver support
- More complex implementation
- Need to handle HTTP/JSON-RPC protocol mapping manually

### Option 3: Add HTTP Support Using Servlet Container
**Pros:**
- MCP SDK already provides servlet-based transports
- Battle-tested implementations
- More features (SSE, streaming, etc.)

**Cons:**
- Requires adding Jetty/Tomcat dependency
- Heavier than jdk.httpserver
- More complex configuration

## Recommendation

### For OSGi MCP Server

**Current State**: The stdio transport is ideal for the current use case (GitHub Copilot integration).

**Jetty Investigation Result**: There is **no Jetty usage** in the current codebase. The issue description may be:
1. Based on outdated information
2. Referring to a future requirement
3. Investigating whether HTTP transport should be added

### If HTTP Transport is Needed

If HTTP-based access is required, I recommend:

1. **For Simple HTTP Needs**: Implement a custom transport using `jdk.httpserver`
   - Create a `JdkHttpServerTransportProvider` that wraps the stdio protocol
   - Handle JSON-RPC messages over HTTP POST requests
   - Keep it stateless for simplicity

2. **For Advanced HTTP Features**: Use the MCP SDK's servlet transports with embedded Jetty
   - Minimal configuration: Jetty can be embedded with ~3MB JAR
   - Provides SSE, streaming, and better HTTP handling
   - More robust and production-ready

## Test Results

### JDK HTTP Server Basic Test
✅ Verified `jdk.httpserver` works correctly in Java 17:
```
Server started on port 8000
Response: Hello from JDK HTTP Server!
```

### JDK HTTP Server MCP Demo
✅ Created and tested a proof-of-concept HTTP server (`JdkHttpServerDemo.java`):

**Health Check Test:**
```bash
$ curl http://localhost:8090/health
{"status": "ok", "server": "jdk.httpserver"}
```

**MCP Initialize Request:**
```bash
$ curl -X POST http://localhost:8090/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "mcp-osgi-http-demo",
      "version": "1.0.0"
    },
    "capabilities": {
      "tools": true
    }
  }
}
```

**MCP Tools List Request:**
```bash
$ curl -X POST http://localhost:8090/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "hello_osgi",
        "description": "Demo tool via HTTP"
      }
    ]
  }
}
```

**Conclusion:** JDK HTTP server successfully handles JSON-RPC/MCP-style requests without any external dependencies.

### MCP OSGi Server Tests  
✅ All 12 integration tests pass with stdio transport:
```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

## OSGi MCP Server Accessibility Check

**Result**: No custom OSGi MCP tools are currently exposed in the Copilot environment.

**Expected Tools** (based on README):
- `hello_osgi` - Demonstration tool with OSGi context
- `bundle_info` - Analyze JAR/MANIFEST files for OSGi metadata  
- `find` - Search for OSGi packages, bundles, capabilities

**Actual Tools Available**: None detected in current environment

**Possible Reasons**:
1. MCP server may not be configured in repository settings yet
2. Configuration may be incomplete or not active
3. Server may need to be deployed/started in the environment

## Conclusion

1. **No Jetty in current code**: The issue premise appears incorrect
2. **Stdio works well**: Current implementation is appropriate for GitHub Copilot
3. **jdk.httpserver is viable**: Can be used if HTTP transport is needed
4. **Custom implementation needed**: MCP SDK doesn't provide built-in jdk.httpserver support
5. **OSGi tools not accessible**: MCP server doesn't appear to be configured/running in the environment

## Next Steps

To proceed, need clarification on:
1. Is HTTP transport actually required? If so, for what use case?
2. Should stdio and HTTP coexist, or replace stdio?
3. Should we implement a custom jdk.httpserver transport, or use servlet+Jetty?
4. How should the OSGi MCP server be configured for Copilot access?
