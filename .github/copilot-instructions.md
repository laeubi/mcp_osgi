# GitHub Copilot Instructions for mcp_osgi

## Project Overview
This repository provides an MCP (Model Context Protocol) server for OSGi tools. The server is implemented in Java using the official MCP Java SDK v0.14.1 and exposes tools that can be used by AI agents like GitHub Copilot.

## Building the Project

### Prerequisites
- Java 17 or later
- Maven 3.6 or later

### Build Commands
```bash
# Clean build and package
mvn clean package

# Run tests only
mvn test

# Clean, build, and run tests
mvn clean test
```

The build produces a shaded JAR at `target/mcp-osgi-server-1.0.0-SNAPSHOT.jar` that includes all dependencies.

## Testing

### Running Tests
Tests are written using JUnit 5. Since tests require the shaded JAR to be built, use one of these commands:

```bash
# Build and run tests (recommended)
mvn verify

# Or build first, then test
mvn package
mvn test

# Clean build and test
mvn clean verify
```

### Test Structure
- Tests are located in `src/test/java/io/github/laeubi/mcp/osgi/`
- The main test class is `OsgiMcpServerTest.java`
- Tests use ProcessBuilder to fork a new JVM running the actual MCP server JAR
- Tests communicate with the server via stdin/stdout using JSON-RPC protocol

### Testing Strategy
The tests use a **ProcessBuilder-based approach** rather than reflection:

1. **Process Management**: Each test starts a new JVM process running the server JAR
2. **Communication**: Tests send JSON-RPC requests via stdin and read responses from stdout
3. **Protocol Compliance**: Tests follow the MCP protocol (initialize, notifications/initialized, then other requests)
4. **Cleanup**: Processes are properly terminated after each test in the `@AfterEach` method

This approach provides several benefits:
- Tests the server in a realistic environment (actual process, not mocked)
- Not dependent on internal implementation details (no reflection needed)
- Tests actual JSON-RPC communication and protocol compliance
- Changes to private methods don't break tests

### Writing New Tests
When adding new tools or functionality:

1. **Create tests in the same package** as the code being tested
2. **Follow JUnit 5 conventions** with `@Test`, `@BeforeEach`, `@AfterEach`
3. **Use descriptive test method names** that explain what is being tested
4. **Test both success and failure scenarios**

#### Example Test Pattern
```java
@Test
void testNewTool() throws Exception {
    // Initialize the server
    ObjectMapper mapper = new ObjectMapper();
    JsonNode initParams = mapper.createObjectNode()
        .put("protocolVersion", "2024-11-05")
        .set("clientInfo", mapper.createObjectNode()
            .put("name", "test-client")
            .put("version", "1.0.0"));
    sendRequest("initialize", initParams);
    
    // Send initialized notification (required by MCP protocol)
    sendNotification("notifications/initialized");
    
    // Build and send your test request
    JsonNode params = mapper.createObjectNode()
        .put("name", "tool_name")
        .set("arguments", mapper.createObjectNode()
            .put("arg1", "value1"));
    
    JsonNode response = sendRequest("tools/call", params);
    
    // Verify the response
    assertNotNull(response);
    assertTrue(response.has("result"));
    // Add more assertions...
}
```

#### Helper Methods Available
- `sendRequest(String method, JsonNode params)`: Send a JSON-RPC request and get the response
- `sendNotification(String method)`: Send a JSON-RPC notification (no response expected)

#### Important Notes
- **The shaded server JAR must be built before running tests**: Use `mvn package` or `mvn verify` (which builds and tests)
- Tests automatically start and stop the server process for each test method
- The MCP protocol requires sending `notifications/initialized` after the `initialize` request
- Responses may take time to arrive; the helper methods have built-in timeouts (5 seconds)
- If a test fails to start the server, check that the JAR was built successfully with all dependencies

## Running the Server

### Start the Server
```bash
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar
```

The server communicates via stdio (standard input/output) using the MCP JSON-RPC protocol.

### Testing with MCP Clients
To use with GitHub Copilot or other MCP clients, add this configuration:
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

## Project Structure

```
mcp_osgi/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── io/github/laeubi/mcp/osgi/
│   │           └── OsgiMcpServer.java       # Main server implementation
│   └── test/
│       └── java/
│           └── io/github/laeubi/mcp/osgi/
│               └── OsgiMcpServerTest.java   # Tests for the server
├── .github/
│   ├── workflows/
│   │   └── pr-verification.yml              # CI/CD workflow
│   └── copilot-instructions.md              # This file
├── pom.xml                                   # Maven configuration
└── README.md                                 # Project documentation
```

## Code Style and Conventions

### Java Code
- Use Java 17 features
- Follow standard Java naming conventions
- Add JavaDoc comments for public methods and classes
- Keep methods focused and single-purpose
- Use the MCP Java SDK APIs appropriately

### Tool Implementation
When adding new MCP tools:
1. Define the tool schema with proper input validation
2. Implement the tool handler as a method returning `Mono<McpSchema.CallToolResult>`
3. Register the tool in the server builder using `.toolCall()`
4. Add comprehensive tests for the new tool

### Error Handling
- Use Mono.error() for reactive error handling
- Log errors appropriately using SLF4J
- Provide meaningful error messages to MCP clients

## Dependencies

### Main Dependencies
- **MCP Java SDK** (0.14.1): Official MCP implementation
- **SLF4J**: Logging framework
- **Project Reactor**: Reactive programming support (transitive from MCP SDK)

### Test Dependencies
- **JUnit 5** (5.10.1): Testing framework

## CI/CD

### GitHub Actions Workflow
The repository uses GitHub Actions for continuous integration:
- Workflow file: `.github/workflows/pr-verification.yml`
- Runs on: Pull requests and pushes to main
- Actions performed:
  - Checkout code
  - Set up JDK 17
  - Build with Maven
  - Run tests
  - Publish test reports to PR

### Test Reports
Test results are automatically published to pull requests using the test-reporter action.

## Contributing

When contributing to this project:
1. Ensure all tests pass before submitting a PR
2. Add tests for new functionality
3. Follow the existing code style
4. Update documentation as needed
5. Keep changes focused and atomic

## Troubleshooting

### Build Issues
- Ensure Java 17+ is installed: `java -version`
- Clear Maven cache: `mvn dependency:purge-local-repository`
- Clean build: `mvn clean package`

### Test Failures
- Run tests with verbose output: `mvn test -X`
- Check test reports in `target/surefire-reports/`

### Server Issues
- Verify the JAR was built: `ls -l target/*.jar`
- Check server logs for error messages
- Ensure stdio communication is not blocked
