# MCP Server Configuration for GitHub Copilot Coding Agent

This directory contains the MCP server configuration for **GitHub Copilot Coding Agent** when used in repository-scoped agent mode from the GitHub web UI.

> **📖 Official Documentation**: [Extend Coding Agent with MCP](https://docs.github.com/en/enterprise-cloud@latest/copilot/how-tos/use-copilot-agents/coding-agent/extend-coding-agent-with-mcp)

## About this Configuration

The `config.json` file in this directory is **required** for GitHub Copilot Coding Agent and is automatically used when:
- You invoke Copilot from the GitHub web interface (github.com)
- The agent runs in GitHub Actions environment
- The MCP server needs to be started and connected to

> **⚠️ Important**: This configuration is **only** for GitHub Copilot Coding Agent in repository-scoped mode. It does **not** apply to local IDE usage (VS Code, JetBrains, etc.).

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
  - **Must include** `"server"` and a port number to start in HTTP/SSE mode
  - The JAR path is set by the `.github/workflows/copilot-setup-steps.yml` workflow
- **`url`**: The SSE (Server-Sent Events) endpoint URL where the agent will connect
  - Format: `http://localhost:{port}/mcp/sse`
  - Must match the port specified in the args

## How It Works

1. GitHub Copilot Coding Agent reads this configuration when started from the web UI
2. It runs the `.github/workflows/copilot-setup-steps.yml` workflow to build and deploy the server JAR
3. It starts the MCP server process using the `command` and `args` specified above
4. It connects to the server via the SSE endpoint specified in `url`
5. The server handles tool calls and returns results to the agent

## Transport Mode: HTTP/SSE (Not stdio)

This configuration uses **HTTP/SSE (Server-Sent Events)** transport:
- The server runs an embedded HTTP server on the specified port (3000)
- GitHub Copilot connects via HTTP and receives updates through SSE
- **This is the only transport mode supported** by GitHub Copilot Coding Agent

> **❌ stdio mode does not work** with GitHub Copilot Coding Agent. The agent requires an HTTP/SSE server endpoint and cannot communicate via stdin/stdout.

## Customization Notes

- **Server port**: You can change the port (default: 3000) but must update both `args` and `url` to match
- **JAR location**: The path `/home/runner/tools/osgi_mcp/server.jar` is set by the setup workflow
- **Workflow file**: Do not modify `.github/workflows/copilot-setup-steps.yml` - the workflow name, job name, and step structure must match GitHub's requirements. Modifications will break GitHub Copilot Coding Agent integration. Only change this file if you need to add additional setup steps or dependencies required by your MCP server.

## Available Tools

The server exposes these tools to the Copilot agent:
- `hello_osgi` - Demonstration tool showing basic MCP server functionality
- `bundle_info` - Analyzes JAR/MANIFEST.MF files for OSGi bundle metadata
- `find` - Searches for OSGi packages, bundles, or capabilities

For more information about the server implementation and local testing, see the main [README.md](../README.md).
