# JDK HTTP Server Demo for MCP

This directory contains a proof-of-concept demonstration of using JDK's built-in HTTP server (`jdk.httpserver`) to serve MCP (Model Context Protocol) requests.

## Overview

The `JdkHttpServerDemo.java` class demonstrates:
- How to create a lightweight HTTP server without external dependencies
- Basic JSON-RPC 2.0 request handling
- MCP protocol initialization and tool listing
- Health check endpoint

## Running the Demo

### Start the Server

```bash
# Build the project first
mvn clean compile

# Run the demo server (defaults to port 8080)
mvn exec:java -Dexec.mainClass="io.github.laeubi.mcp.osgi.JdkHttpServerDemo"

# Or specify a different port
mvn exec:java -Dexec.mainClass="io.github.laeubi.mcp.osgi.JdkHttpServerDemo" -Dexec.args="8090"
```

### Test the Server

**Health Check:**
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{"status": "ok", "server": "jdk.httpserver"}
```

**MCP Initialize:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

Expected response:
```json
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

**MCP Tools List:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

Expected response:
```json
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

## Key Features Demonstrated

1. **No External Dependencies**: Uses only JDK built-in components
2. **JSON-RPC 2.0 Validation**: Checks for proper request structure
3. **MCP Protocol Basics**: Handles initialize and tools/list methods
4. **CORS Support**: Includes Access-Control-Allow-Origin header for web clients
5. **Health Check**: Simple /health endpoint for monitoring

## Limitations

This is a **demonstration only**, not a complete MCP transport implementation:

- Does not integrate with the MCP SDK's transport layer
- Handles only a few demo methods (initialize, tools/list)
- No session management or state tracking
- No support for MCP streaming or Server-Sent Events
- Not designed for production use

## Comparison: jdk.httpserver vs Servlet Container

### jdk.httpserver Advantages
- ✅ No external dependencies
- ✅ Simple API
- ✅ Lightweight (~100 lines of code for basic server)
- ✅ Good for demos and simple use cases

### jdk.httpserver Limitations
- ❌ Not production-grade
- ❌ Limited features (no servlet API, no advanced HTTP)
- ❌ Manual protocol handling required
- ❌ No built-in SSE support

### Servlet Container (Jetty/Tomcat) Advantages
- ✅ Production-ready
- ✅ Rich feature set (SSE, WebSockets, streaming)
- ✅ MCP SDK provides built-in servlet transports
- ✅ Battle-tested and performant

### Servlet Container Limitations
- ❌ Requires external dependency (~3-10 MB)
- ❌ More complex configuration
- ❌ Heavier than jdk.httpserver

## Recommendation

For the MCP OSGi Server:
- **Use stdio transport (current)** for GitHub Copilot integration ✅
- **Add jdk.httpserver** if simple HTTP access is needed for demos/testing
- **Use servlet + Jetty** if production HTTP transport is required

## Files

- `JdkHttpServerDemo.java` - Main demo server implementation
- `DEMO_README.md` - This file
- `INVESTIGATION.md` - Full investigation report on jdk.httpserver usage

## See Also

- [INVESTIGATION.md](INVESTIGATION.md) - Complete analysis of jdk.httpserver for MCP
- [JDK HTTP Server Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/module-summary.html)
- [MCP Specification](https://modelcontextprotocol.io/)
