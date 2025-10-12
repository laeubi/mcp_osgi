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

### `build-mcp-server.yml` (Reusable Workflow)
A reusable workflow that can be called from other repositories to build the MCP OSGi Server JAR. This is intended for use with the GitHub Copilot Coding Agent.

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
- Checks out the `laeubi/mcp_osgi` repository
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
