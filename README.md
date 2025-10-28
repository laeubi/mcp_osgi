# mcp_osgi
A MCP server dedicated to bring tools useful in OSGi context

> **Note**: This project now uses the [official MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) (v0.14.1) instead of a homebrew implementation. This provides a standardized, production-ready implementation of the Model Context Protocol with better maintainability and feature support.

## Introduction

### What is MCP?

The Model Context Protocol (MCP) is an open protocol that enables seamless integration between LLM applications and external data sources and tools. MCP provides:

- **Standardized Integration**: A universal protocol for connecting AI assistants with various tools and data sources
- **Tool Exposure**: A way to expose tools that can be discovered and invoked by AI agents
- **Bi-directional Communication**: JSON-RPC 2.0 based protocol for client-server communication
- **Rich Capabilities**: Support for resources, prompts, and tools with structured schemas

MCP is particularly useful for AI workflows like GitHub Copilot Coding Agents, which can use MCP servers to access specialized tools and domain-specific functionality.

### What is OSGi?

OSGi (Open Services Gateway initiative) is a Java framework for developing and deploying modular software programs and libraries. Key features include:

- **Modularity**: Applications are built from reusable bundles (JAR files with metadata)
- **Dynamic Service Model**: Services can be dynamically installed, started, stopped, updated, and uninstalled at runtime
- **Versioning**: Sophisticated dependency management with version ranges
- **Service Registry**: A dynamic service registry for loose coupling between components
- **Lifecycle Management**: Complete control over bundle lifecycle

OSGi is widely used in enterprise applications, IDE platforms (like Eclipse), and embedded systems.

## Preliminary Work & Use Cases

### Current Implementation Status

This project provides an example MCP server implementation that exposes OSGi-related tools through the MCP protocol, enabling AI agents to interact with OSGi environments.

**Example Tools Implemented:**
- `hello_osgi` - A demonstration tool showing basic MCP server functionality with OSGi context

### Potential OSGi Tools for MCP Exposure

The following OSGi tools and commands would be valuable to expose through MCP for AI-assisted development:

#### Bundle Management
- **list_bundles** - List all installed bundles with their state, version, and symbolic name
- **bundle_info** - Get detailed information about a specific bundle
- **install_bundle** - Install a new bundle from a URL or local path
- **start_bundle** - Start a specific bundle
- **stop_bundle** - Stop a specific bundle
- **update_bundle** - Update an existing bundle
- **uninstall_bundle** - Uninstall a bundle

#### Service Management
- **list_services** - List all registered OSGi services
- **service_info** - Get detailed information about a service (interfaces, properties, using bundles)
- **service_references** - Find service references matching a filter

#### Dependency Analysis
- **check_dependencies** - Analyze bundle dependencies and identify missing requirements
- **why_not_resolved** - Diagnose why a bundle is not resolved
- **package_wiring** - Show package wiring between bundles
- **capability_requirements** - Analyze bundle capabilities and requirements

#### Configuration & Management
- **list_configurations** - List Configuration Admin configurations
- **get_configuration** - Retrieve a specific configuration
- **update_configuration** - Update configuration properties
- **delete_configuration** - Delete a configuration

#### Diagnostics & Troubleshooting
- **diagnose_framework** - Get OSGi framework state and diagnostics
- **bundle_headers** - Display bundle manifest headers
- **bundle_classpath** - Show bundle classpath entries
- **find_class** - Find which bundle exports a specific class/package

#### Build & Development
- **resolve_bundles** - Attempt to resolve bundles
- **refresh_bundles** - Refresh bundle wiring
- **validate_manifest** - Validate a bundle manifest file
- **generate_manifest** - Generate OSGi manifest from Maven/Gradle metadata

### Benefits for AI-Assisted Development

By exposing OSGi tools through MCP, AI coding agents can:

1. **Understand OSGi Applications**: Analyze bundle structures, dependencies, and service relationships
2. **Troubleshoot Issues**: Diagnose bundle resolution problems, missing dependencies, and configuration issues
3. **Automate Tasks**: Perform routine OSGi management tasks like installing, starting, and configuring bundles
4. **Generate Code**: Create properly structured OSGi bundles with correct manifests and metadata
5. **Provide Guidance**: Offer context-aware suggestions based on actual OSGi framework state

## Example: Hello OSGi MCP Server

This repository includes a Maven-based example MCP server that demonstrates how to:

1. Set up a Java MCP server using the [official MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
2. Define and expose tools through the MCP protocol
3. Implement tool handlers with OSGi context
4. Support multiple transport modes: stdio and HTTP with SSE (Server-Sent Events)

The server uses the official **MCP Java SDK v0.14.1** which provides:
- Standardized implementation of the Model Context Protocol
- Built-in stdio transport for inter-process communication
- HTTP/SSE transport for server-based deployments
- Asynchronous server capabilities with Project Reactor
- JSON schema validation and tool registration APIs

### Building and Running

```bash
# Build the project
mvn clean package

# Run the MCP server in stdio mode (default - communicates via stdin/stdout)
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar

# Run the MCP server in server mode (HTTP server with SSE transport)
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar server

# Run the MCP server in server mode on a specific port
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar server 8080
```

The server supports two modes:
- **stdio mode** (default): Communicates via JSON-RPC 2.0 over stdin/stdout. This is suitable for process-based MCP clients.
- **server mode**: Runs an embedded HTTP server with SSE (Server-Sent Events) transport. This is suitable for repository-based MCP configurations (like GitHub Copilot) that don't support stdio.

### Testing the Server

A test script is provided to demonstrate basic interaction with the MCP server in stdio mode:

```bash
# Make the script executable (if not already)
chmod +x test-mcp-server.sh

# Run the test
./test-mcp-server.sh
```

To manually test the server in stdio mode, you can pipe JSON-RPC requests to it:

```bash
# Start the server
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar

# In another terminal, send a request (example):
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0"}}}' | java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

To test the server in server mode, start it with the `server` parameter and access it via HTTP:

```bash
# Start the server in server mode on port 3000
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar server 3000

# In another terminal, access the SSE endpoint:
curl http://localhost:3000/mcp/sse
```

### Using with GitHub Copilot

To use this MCP server with GitHub Copilot or other MCP clients, you can configure the client to connect using either stdio or HTTP transport.

#### Stdio Mode (Traditional)

For traditional MCP clients that support stdio transport:

#### Option 1: Using the Shared GitHub Actions Workflow (Recommended for GitHub Copilot Coding Agent)

This repository provides a reusable GitHub Actions workflow that automatically builds the MCP server JAR for use with the GitHub Copilot Coding Agent. This is the recommended approach for integrating the MCP server into your GitHub Copilot environment.

According to the [GitHub Copilot Coding Agent documentation](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/customize-the-agent-environment), you can customize the agent environment by adding additional tools and MCP servers.

**Step 1: Create a workflow in your repository**

Create a file `.github/workflows/setup-copilot-mcp.yml` in your repository:

```yaml
name: Setup Copilot MCP Server

on:
  workflow_dispatch:  # Manual trigger via GitHub UI
  push:
    branches: [ main ]  # Or trigger on push to main

jobs:
  build-mcp-server:
    name: Build MCP OSGi Server
    uses: laeubi/mcp_osgi/.github/workflows/build-mcp-server.yml@main
```

See [.github/workflows/example-copilot-setup.yml.example](.github/workflows/example-copilot-setup.yml.example) for a complete example with additional configuration steps.

**What the workflow does:**

The reusable workflow will:
1. Checkout the `laeubi/mcp_osgi` repository
2. Install Java 17
3. Build the MCP server JAR using Maven
4. Upload the JAR as an artifact named `mcp-osgi-server`

**Step 2: Download and use the MCP server**

After the workflow runs, the built JAR will be available as a workflow artifact. You can:

- **Download it manually**: Go to the workflow run in GitHub Actions and download the `mcp-osgi-server` artifact
- **Use in subsequent workflow steps**: Use the `actions/download-artifact@v4` action to download and use the JAR in your workflow
- **Configure locally**: Download the artifact and configure it in your local GitHub Copilot settings

**Step 3: Configure for GitHub Copilot Coding Agent**

Once you have the JAR file, configure your GitHub Copilot to use it. 

**For stdio mode** (traditional MCP client configuration):

```json
{
  "mcpServers": {
    "osgi": {
      "command": "java",
      "args": ["-jar", "/path/to/downloaded/mcp-osgi-server-1.0.0-SNAPSHOT.jar"],
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

**For server mode** (recommended for repository-based configurations):

First, start the server in server mode:
```bash
java -jar /path/to/mcp-osgi-server-1.0.0-SNAPSHOT.jar server 3000
```

Then configure your MCP client to connect to the HTTP endpoint:
```json
{
  "mcpServers": {
    "osgi": {
      "url": "http://localhost:3000/mcp",
      "description": "MCP server providing OSGi tools for AI agents"
    }
  }
}
```

Replace `/path/to/downloaded/mcp-osgi-server-1.0.0-SNAPSHOT.jar` with the actual path where you extracted the downloaded artifact.

#### Option 2: Manual Configuration (Local Use)

For local use or manual configuration with GitHub Copilot, build the JAR locally:

```bash
# Clone and build the MCP server
git clone https://github.com/laeubi/mcp_osgi.git
cd mcp_osgi
mvn clean package
```

Then configure your MCP client (see `mcp-client-config-example.json`):
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

For GitHub Copilot, place this configuration in your MCP settings file (typically `~/.mcp/settings.json` or as configured in your IDE).

## Project Structure

```
mcp_osgi/
├── .github/
│   ├── workflows/
│   │   ├── pr-verification.yml                            # CI workflow for PR verification
│   │   ├── build-mcp-server.yml                           # Reusable workflow for building MCP server
│   │   └── example-copilot-setup.yml.example              # Example workflow for Copilot integration
│   └── copilot-instructions.md                            # GitHub Copilot instructions
├── src/
│   └── main/
│       └── java/
│           └── io/
│               └── github/
│                   └── laeubi/
│                       └── mcp/
│                           └── osgi/
│                               └── OsgiMcpServer.java    # Main MCP server implementation
├── pom.xml                                                # Maven build configuration
├── test-mcp-server.sh                                     # Test script for the server
├── mcp-client-config-example.json                         # Example MCP client configuration
├── .gitignore                                             # Git ignore patterns
├── README.md                                              # This file
└── LICENSE                                                # Eclipse Public License 2.0
```

### Key Files

- **OsgiMcpServer.java**: The main MCP server implementation using the official MCP Java SDK that exposes the `hello_osgi` tool
- **pom.xml**: Maven configuration with dependencies for the MCP Java SDK (v0.14.1) and SLF4J (logging)
- **test-mcp-server.sh**: Shell script to demonstrate server interaction
- **mcp-client-config-example.json**: Example configuration for MCP clients like GitHub Copilot
- **build-mcp-server.yml**: Reusable GitHub Actions workflow for building the MCP server JAR
- **example-copilot-setup.yml.example**: Example workflow showing how to use the reusable workflow in your repository

### Implementation Details

The server implementation leverages the official MCP Java SDK:

1. **Transport Layer**: Uses `StdioServerTransportProvider` for stdio-based communication
2. **Server Type**: Built with `McpServer.async()` for non-blocking asynchronous operations
3. **Tool Registration**: Tools are registered using the SDK's `toolCall()` method with proper schema definitions
4. **JSON Handling**: Uses the SDK's built-in Jackson integration for JSON serialization
5. **Reactive Streams**: Tool handlers return `Mono<CallToolResult>` for reactive processing

## Contributing

Contributions are welcome! Please feel free to submit pull requests with:
- Additional OSGi tools for MCP exposure
- Improvements to existing tools
- Documentation enhancements
- Bug fixes

## License

This project is licensed under the Eclipse Public License 2.0 - see the [LICENSE](LICENSE) file for details.
