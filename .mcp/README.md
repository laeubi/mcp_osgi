# MCP Server Configuration for GitHub Copilot Coding Agent

This directory contains the MCP server configuration for GitHub Copilot Coding Agent when used from the **GitHub web UI**.

## About this Configuration

The `config.json` file in this directory is used by GitHub Copilot Coding Agent when:
- You invoke Copilot from the GitHub web interface (github.com)
- The agent runs in GitHub Actions environment
- The MCP server needs to be started and connected to

## Configuration Format

```json
{
  "mcpServers": {
    "osgi": {
      "command": "java",
      "args": ["-jar", "/home/runner/tools/osgi_mcp/server.jar", "server", "3000"],
      "url": "http://localhost:3000/mcp/sse"
    }
  }
}
```

### Configuration Fields

- **`command`**: The command to start the MCP server process
- **`args`**: Arguments passed to the command
  - Must include `"server"` and a port number to start in HTTP/SSE mode
  - The JAR path is set by the `copilot-setup-steps.yml` workflow
- **`url`**: The SSE endpoint URL where the agent will connect
  - Format: `http://localhost:{port}/mcp/sse`
  - Must match the port specified in the args

## How It Works

1. GitHub Copilot Coding Agent reads this configuration when started from the web UI
2. It runs the `copilot-setup-steps.yml` workflow to build and deploy the server JAR
3. It starts the MCP server process using the `command` and `args`
4. It connects to the server via the SSE endpoint specified in `url`
5. The server handles tool calls and returns results to the agent

## Important Notes

⚠️ **This configuration is NOT for local IDE use!**

- **Repository config** (`.mcp/config.json`): For GitHub Copilot Agent in web UI
  - Uses HTTP/SSE server mode
  - Starts server as a subprocess
  
- **Local IDE config** (e.g., VS Code settings): For local development
  - Typically uses stdio mode
  - See `mcp-client-config-example.json` for local IDE configuration examples

## Transport Mode

This configuration uses **HTTP/SSE (Server-Sent Events)** transport, not stdio:
- The server runs an embedded HTTP server on the specified port
- GitHub Copilot connects via HTTP and receives updates through SSE
- This is required for the web UI agent mode running in GitHub Actions

For more information, see the main [README.md](../README.md) documentation.
