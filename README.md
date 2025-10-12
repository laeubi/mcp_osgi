# mcp_osgi
A MCP server dedicated to bring tools useful in OSGi context

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

1. Set up a Java MCP server using the MCP Java SDK
2. Define and expose tools through the MCP protocol
3. Implement tool handlers with OSGi context
4. Handle JSON-RPC communication with MCP clients

### Building and Running

```bash
# Build the project
mvn clean package

# Run the MCP server (communicates via stdio)
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

### Testing the Server

A test script is provided to demonstrate basic interaction with the MCP server:

```bash
# Make the script executable (if not already)
chmod +x test-mcp-server.sh

# Run the test
./test-mcp-server.sh
```

To manually test the server, you can pipe JSON-RPC requests to it:

```bash
# Start the server
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar

# In another terminal, send a request (example):
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0"}}}' | java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

### Using with GitHub Copilot

To use this MCP server with GitHub Copilot or other MCP clients, configure the client to connect to the server via stdio transport.

Example MCP client configuration (see `mcp-client-config-example.json`):
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

- **OsgiMcpServer.java**: The main MCP server implementation that handles JSON-RPC requests and exposes the `hello_osgi` tool
- **pom.xml**: Maven configuration with dependencies for Jackson (JSON processing) and SLF4J (logging)
- **test-mcp-server.sh**: Shell script to demonstrate server interaction
- **mcp-client-config-example.json**: Example configuration for MCP clients like GitHub Copilot

## Contributing

Contributions are welcome! Please feel free to submit pull requests with:
- Additional OSGi tools for MCP exposure
- Improvements to existing tools
- Documentation enhancements
- Bug fixes

## License

This project is licensed under the Eclipse Public License 2.0 - see the [LICENSE](LICENSE) file for details.
