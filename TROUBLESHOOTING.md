# MCP Server Path Configuration - Troubleshooting Guide

## Problem

When using the MCP OSGi server with GitHub Copilot Coding Agent, users encountered an error:

```
Error: Unable to access jarfile /home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

This occurred because the MCP server configuration referenced a path that didn't exist.

## Root Cause

The repository is cloned at `/home/runner/work/mcp_osgi/mcp_osgi/`, and the JAR is built at:
```
/home/runner/work/mcp_osgi/mcp_osgi/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

However, the MCP client configuration was trying to use:
```
/home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

The path was missing the `/work/mcp_osgi/mcp_osgi/` portion.

## Solution

The fix involves two parts:

### 1. Updated Setup Workflow

The `.github/copilot-setup-steps.yml` file now includes a step to copy the JAR to a predictable location:

```yaml
- name: Copy JAR to standard location
  run: |
    mkdir -p /home/runner/target
    cp target/mcp-osgi-server-1.0.0-SNAPSHOT.jar /home/runner/target/
    echo "MCP server JAR copied to /home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"
```

This ensures the JAR is available at the expected path: `/home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar`

### 2. Updated Configuration Example

A new configuration file `mcp-client-config-copilot-agent.json` provides the correct settings for GitHub Copilot Coding Agent:

```json
{
  "mcpServers": {
    "osgi": {
      "type": "local",
      "command": "java",
      "args": [
        "-jar",
        "/home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"
      ],
      "tools": ["hello_osgi"],
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

**Important Notes:**
- Remove the `"server"` argument that was in the original failing configuration - it's not supported
- Include the `"type": "local"` field for Copilot Coding Agent
- The `"tools"` array lists the available tools (currently `["hello_osgi"]`)

## Verification

To verify the fix works:

1. The setup workflow copies the JAR to `/home/runner/target/`
2. The JAR is executable: `java -jar /home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar`
3. The server responds correctly to MCP protocol requests
4. All integration tests pass

## Usage

### For GitHub Copilot Coding Agent

Use the configuration from `mcp-client-config-copilot-agent.json`:

```json
{
  "mcpServers": {
    "osgi": {
      "type": "local",
      "command": "java",
      "args": ["-jar", "/home/runner/target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"],
      "tools": ["hello_osgi"],
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

### For Local Development

When running locally, use relative paths:

```json
{
  "mcpServers": {
    "osgi": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/mcp-osgi-server-1.0.0-SNAPSHOT.jar"],
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

Replace `/absolute/path/to/` with the actual path where you've built or downloaded the JAR.

## Files Changed

1. `.github/copilot-setup-steps.yml` - Added JAR copy step
2. `mcp-client-config-copilot-agent.json` - New file with correct Copilot Agent configuration
3. `README.md` - Updated documentation with configuration instructions
