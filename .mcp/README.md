# MCP Configuration

This directory contains the MCP (Model Context Protocol) server configuration for the GitHub Copilot Coding Agent web UI.

## Important: Two Different Configuration Approaches

This repository supports **two distinct ways** to use the MCP OSGi server:

### 1. GitHub Copilot Coding Agent (Web UI) 🌐

**Configuration location**: `.mcp/config.json` (this directory)

The configuration in this directory is automatically used by the GitHub Copilot Coding Agent when it runs in the web UI (via GitHub Actions).

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

**Key features**:
- Uses **HTTP/SSE transport** (Server-Sent Events)
- Server runs in "server" mode on port 3000
- Agent connects to SSE endpoint at `/mcp/sse`
- Configured in repository, managed by GitHub
- Automatically set up via `.github/copilot-setup-steps.yml`

### 2. Local IDE (VS Code, JetBrains, etc.) 💻

**Configuration location**: Your IDE's MCP settings (NOT this directory)

For local development, configure the MCP server in your IDE settings using **stdio transport**:

```json
{
  "mcpServers": {
    "osgi": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/mcp-osgi-server-1.0.0-SNAPSHOT.jar"],
      "tools": ["hello_osgi", "bundle_info", "find"]
    }
  }
}
```

**Key features**:
- Uses **stdio transport** (stdin/stdout)
- Server runs in default stdio mode
- Direct process communication
- Configured locally by each developer
- Path points to your local build

## Configuration Matrix

| Environment | Transport | Config Location | Endpoint | Server Mode |
|------------|-----------|----------------|----------|-------------|
| GitHub Web UI | HTTP/SSE | `.mcp/config.json` | `/mcp/sse` | `server 3000` |
| Local IDE | stdio | IDE settings | N/A | (default) |

## Why Two Different Approaches?

- **GitHub Web UI**: Runs in a sandboxed GitHub Actions environment where HTTP/SSE transport is more reliable and secure
- **Local IDE**: Runs on your machine where direct process communication (stdio) is simpler and more efficient

## Documentation

For more details, see:
- [README.md](../README.md) - Main project documentation
- [.github/copilot-instructions.md](../.github/copilot-instructions.md) - Copilot-specific instructions
- [Official GitHub docs](https://docs.github.com/en/enterprise-cloud@latest/copilot/how-tos/use-copilot-agents/coding-agent/extend-coding-agent-with-mcp) - MCP configuration for Copilot Coding Agent
