# GitHub Actions Workflows

This directory contains GitHub Actions workflows for building and distributing the MCP OSGi Server.

## Workflows

### `pr-verification.yml`
CI workflow that runs on pull requests and pushes to main. It:
- Checks out the code
- Sets up Java 17
- Builds the project with Maven
- Runs tests
- Publishes test reports

### `copilot-setup-steps.yml`
Composite action used by the GitHub Copilot Coding Agent to set up the environment for this repository. This is the **recommended setup for in-repository use**.

**What it does:**
- Checks out the repository code
- Sets up JDK 21 with Maven
- Builds the MCP server JAR in `target/` directory

**Configuration for GitHub Copilot:**

When using this repository with GitHub Copilot Coding Agent, configure your MCP server settings as follows:

```json
{
  "mcpServers": {
    "osgi": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"],
      "tools": ["hello_osgi", "bundle_info", "find"]
    }
  }
}
```

**Note:** The `tools` field is optional (tools are auto-discovered), but listing them makes it easy to see what's available.

The Copilot Coding Agent will automatically run `copilot-setup-steps.yml` to build the JAR before using it.

For more information, see the [GitHub Copilot Coding Agent documentation](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/customize-the-agent-environment).

### `build-mcp-server.yml` (Reusable Workflow - For External Use)
A reusable workflow that can be called from other repositories to build the MCP OSGi Server JAR. This is for users who want to integrate the MCP server into their own repositories.

**Usage in your repository:**

Create `.github/workflows/setup-copilot-mcp.yml`:

```yaml
name: Setup Copilot MCP Server

on:
  workflow_dispatch:  # Manual trigger
  push:
    branches: [ main ]

jobs:
  build-mcp-server:
    uses: laeubi/mcp_osgi/.github/workflows/build-mcp-server.yml@main
```

**What it does:**
- Checks out the `laeubi/mcp_osgi` repository into a subdirectory
- Sets up Java 17 with Maven cache
- Builds the MCP server JAR using `mvn clean package`
- Uploads the JAR as an artifact named `mcp-osgi-server`

**Outputs:**
- `jar-path`: The absolute path to the built JAR file in the workflow workspace

**Artifacts:**
- `mcp-osgi-server`: The built JAR file (retained for 90 days)

### `example-copilot-setup.yml.example`
An example workflow demonstrating how to use the reusable `build-mcp-server.yml` workflow in your own repository. This includes additional steps for downloading and configuring the MCP server.

**To use this example:**
1. Copy it to your repository as `.github/workflows/setup-copilot-mcp.yml`
2. Remove the `.example` extension
3. Customize as needed for your use case

## For More Information

See the main [README.md](../../README.md) for complete documentation on using the MCP OSGi Server with GitHub Copilot.
