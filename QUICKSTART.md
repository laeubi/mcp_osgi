# Quick Start: GitHub Copilot Integration

This guide helps you quickly verify and use the MCP OSGi server with GitHub Copilot Coding Agent.

## Prerequisites Check

Run the verification script to ensure your environment is set up correctly:

```bash
./verify-environment.sh
```

This checks:
- ✓ Java 17+ and Maven 3.9+ installation
- ✓ JAR file build and deployment
- ✓ Server functionality (stdio and HTTP/SSE modes)
- ✓ MCP environment variables
- ✓ Workspace accessibility

## See It In Action

Run the demonstration to see all 3 OSGi tools working:

```bash
./demo-tools.sh
```

This demonstrates:
- **hello_osgi** - Greeting with Java/OS context
- **bundle_info** - OSGi bundle analysis
- **find** - Package/bundle search

## Configuration for GitHub Copilot

The correct configuration is in `mcp-client-config-copilot-agent.json`:

```json
{
  "mcpServers": {
    "osgi": {
      "type": "local",
      "command": "java",
      "args": [
        "-jar",
        "/home/runner/tools/osgi_mcp/server.jar",
        "server"
      ],
      "tools": ["hello_osgi", "bundle_info", "find"],
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

### Key Configuration Points

1. **type: "local"** - Required for GitHub Copilot (not "stdio")
2. **server argument** - Starts HTTP/SSE mode (required for Copilot)
3. **Absolute path** - `/home/runner/tools/osgi_mcp/server.jar`

## Setup Process

The `.github/copilot-setup-steps.yml` workflow:

1. Builds the MCP server JAR
2. Copies it to `/home/runner/tools/osgi_mcp/server.jar`
3. Makes it available for GitHub Copilot sessions

## Troubleshooting

### Server Not Found

```bash
# Manually set up the JAR
mkdir -p /home/runner/tools/osgi_mcp
mvn clean package -DskipTests
cp target/mcp-osgi-server-1.0.0-SNAPSHOT.jar /home/runner/tools/osgi_mcp/server.jar
```

### Test Server Manually

**stdio mode:**
```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0"}}}' | \
  java -jar /home/runner/tools/osgi_mcp/server.jar
```

**Server mode (HTTP/SSE):**
```bash
java -jar /home/runner/tools/osgi_mcp/server.jar server 3000 &
curl http://localhost:3000/mcp/sse
```

### Check MCP Environment

```bash
# Check if MCP is enabled
echo "COPILOT_MCP_ENABLED=$COPILOT_MCP_ENABLED"

# Check MCP config location
echo "COPILOT_AGENT_MCP_SERVER_TEMP=$COPILOT_AGENT_MCP_SERVER_TEMP"

# List registered tools
jq 'keys' "$COPILOT_AGENT_MCP_SERVER_TEMP/mcp-config.json"
```

## Available Tools

### 1. hello_osgi
```json
{
  "name": "hello_osgi",
  "arguments": {
    "name": "YourName"
  }
}
```

Returns a greeting with Java and OS context information.

### 2. bundle_info
```json
{
  "name": "bundle_info",
  "arguments": {
    "file": "/path/to/bundle.jar"
  }
}
```

Analyzes a JAR or MANIFEST.MF file for OSGi metadata including symbolic name, version, dependencies, and capabilities.

### 3. find
```json
{
  "name": "find",
  "arguments": {
    "type": "package",
    "name": "org.osgi.framework"
  }
}
```

Searches for OSGi packages, bundles, or capabilities and returns download information.

## More Information

- **Full Investigation**: See `INVESTIGATION_SUMMARY.md`
- **Integration Tests**: Run `./test-mcp-integration.sh`
- **Main Documentation**: See `README.md`

## Quick Tips

✅ **Always use absolute paths** in Copilot environment
✅ **Use "server" mode** for Copilot (not stdio)
✅ **Verify before asking Copilot** - run `./verify-environment.sh`
✅ **Test tools first** - run `./demo-tools.sh`

## Support

If you encounter issues:

1. Run `./verify-environment.sh` to diagnose
2. Check `INVESTIGATION_SUMMARY.md` for common issues
3. Verify configuration matches `mcp-client-config-copilot-agent.json`
4. Ensure `.github/copilot-setup-steps.yml` completed successfully
