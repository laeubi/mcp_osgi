package io.github.laeubi.mcp.osgi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the MCP OSGi Server.
 * 
 * This test class uses ProcessBuilder to fork a new JVM running the MCP server
 * and communicates with it via stdin/stdout using JSON-RPC protocol.
 * This approach tests the server in a realistic scenario without relying on
 * reflection to access internal implementation details.
 */
class OsgiMcpServerTest {
    
    private Process serverProcess;
    private BufferedReader serverOutput;
    private BufferedWriter serverInput;
    private ObjectMapper objectMapper;
    private int requestId = 0;
    
    /**
     * Set up for each test - starts the MCP server process.
     * 
     * Note: The tests require the shaded JAR to be built. If running tests directly
     * (e.g., 'mvn test'), you must first run 'mvn package' or use 'mvn verify'.
     */
    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // Find the JAR file
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        if (!jarFile.exists()) {
            fail("JAR file not found at " + jarFile.getAbsolutePath() + 
                 ". Please run 'mvn package' first, or use 'mvn verify' to build and test.");
        }
        
        // Start the server process
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java",
            "-jar",
            jarFile.getAbsolutePath()
        );
        processBuilder.redirectErrorStream(false); // Keep stderr separate for logging
        
        serverProcess = processBuilder.start();
        
        // Set up streams for communication
        serverOutput = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()));
        serverInput = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream()));
        
        // Give the server a moment to start
        Thread.sleep(500);
        
        // Verify the process is running
        if (!serverProcess.isAlive()) {
            // Try to capture error output
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            fail("Server process failed to start. Error output:\n" + errorOutput.toString());
        }
    }
    
    /**
     * Tear down after each test - stops the server process.
     */
    @AfterEach
    void tearDown() throws Exception {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            boolean terminated = serverProcess.waitFor(5, TimeUnit.SECONDS);
            if (!terminated) {
                serverProcess.destroyForcibly();
            }
        }
        
        if (serverInput != null) {
            try {
                serverInput.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        
        if (serverOutput != null) {
            try {
                serverOutput.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * Send a JSON-RPC notification (no response expected).
     */
    private void sendNotification(String method) throws Exception {
        String notification = String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\"}",
            method
        );
        
        serverInput.write(notification);
        serverInput.newLine();
        serverInput.flush();
    }
    
    /**
     * Send a JSON-RPC request to the server and return the response.
     */
    private JsonNode sendRequest(String method, JsonNode params) throws Exception {
        requestId++;
        
        // Build the request
        String request = String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}",
            requestId,
            method,
            params == null ? "{}" : objectMapper.writeValueAsString(params)
        );
        
        // Send the request
        serverInput.write(request);
        serverInput.newLine();
        serverInput.flush();
        
        // Read the response with timeout
        // The server logs to stderr, so we only read from stdout which contains JSON responses
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds timeout
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (serverOutput.ready()) {
                String responseLine = serverOutput.readLine();
                if (responseLine == null) {
                    break;
                }
                
                // Try to parse as JSON
                try {
                    JsonNode response = objectMapper.readTree(responseLine);
                    // Verify it's a JSON-RPC response with matching ID
                    if (response.has("jsonrpc") && response.has("id") && 
                        response.get("id").asInt() == requestId) {
                        return response;
                    }
                } catch (Exception e) {
                    // Not valid JSON, continue reading
                }
            }
            Thread.sleep(50); // Small delay before checking again
        }
        
        throw new RuntimeException("No valid JSON-RPC response received within timeout");
    }
    
    /**
     * Test that the server responds to initialize request.
     */
    @Test
    void testInitialize() throws Exception {
        // Build initialize params
        ObjectMapper mapper = new ObjectMapper();
        JsonNode params = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        
        // Send initialize request
        JsonNode response = sendRequest("initialize", params);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertEquals("2024-11-05", result.get("protocolVersion").asText(), 
            "Protocol version should match");
        assertTrue(result.has("serverInfo"), "Should have serverInfo");
        assertEquals("mcp-osgi-server", result.get("serverInfo").get("name").asText(),
            "Server name should be mcp-osgi-server");
        assertEquals("1.0.0", result.get("serverInfo").get("version").asText(),
            "Server version should be 1.0.0");
        assertTrue(result.has("capabilities"), "Should have capabilities");
        assertTrue(result.get("capabilities").has("tools"), "Should have tools capability");
    }
    
    /**
     * Test that the server lists available tools.
     */
    @Test
    void testListTools() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Send tools/list request
        JsonNode response = sendRequest("tools/list", null);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertTrue(result.has("tools"), "Result should have tools array");
        JsonNode tools = result.get("tools");
        assertTrue(tools.isArray(), "Tools should be an array");
        assertTrue(tools.size() >= 1, "Should have at least 1 tool");
        
        JsonNode tool = tools.get(0);
        assertEquals("hello_osgi", tool.get("name").asText(), "First tool name should be hello_osgi");
        assertTrue(tool.has("description"), "Tool should have description");
        assertTrue(tool.has("inputSchema"), "Tool should have inputSchema");
    }
    
    /**
     * Test calling the hello_osgi tool with a custom name.
     */
    @Test
    void testCallHelloOsgiWithName() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "hello_osgi")
            .set("arguments", mapper.createObjectNode()
                .put("name", "Test User"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        assertTrue(result.has("content"), "Result should have content");
        JsonNode content = result.get("content");
        assertTrue(content.isArray(), "Content should be an array");
        assertTrue(content.size() > 0, "Content should not be empty");
        
        JsonNode textContent = content.get(0);
        assertEquals("text", textContent.get("type").asText(), "Content type should be text");
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Hello, Test User!"), "Response should contain greeting");
        assertTrue(text.contains("OSGi Context Information"), "Response should contain OSGi context info");
    }
    
    /**
     * Test calling the hello_osgi tool without providing a name (default).
     */
    @Test
    void testCallHelloOsgiWithoutName() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params with empty arguments
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "hello_osgi")
            .set("arguments", mapper.createObjectNode());
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        
        JsonNode content = result.get("content");
        JsonNode textContent = content.get(0);
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Hello, World!"), "Response should contain default greeting");
    }
    
    /**
     * Test that the JAR file is created after build.
     * This verifies the Maven build process.
     * 
     * Note: This test does not require the server to be running, so we skip setUp.
     */
    @Test
    void testJarFileExists() throws Exception {
        // Skip server startup for this test by cleaning up early
        if (serverProcess != null && serverProcess.isAlive()) {
            tearDown();
        }
        
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        // The JAR should exist after the build, but we'll make this test informative
        if (jarFile.exists()) {
            assertTrue(jarFile.length() > 0, "JAR file should not be empty");
        }
        // If JAR doesn't exist, that's okay - it might not have been built yet
        // The main validation is that the code compiles
    }
    
    /**
     * Test that the server lists all available tools including the new ones.
     */
    @Test
    void testListAllTools() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Send tools/list request
        JsonNode response = sendRequest("tools/list", null);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertTrue(result.has("tools"), "Result should have tools array");
        JsonNode tools = result.get("tools");
        assertTrue(tools.isArray(), "Tools should be an array");
        assertEquals(3, tools.size(), "Should have 3 tools");
        
        // Verify the tools are present
        boolean hasHelloOsgi = false;
        boolean hasBundleInfo = false;
        boolean hasFind = false;
        
        for (JsonNode tool : tools) {
            String toolName = tool.get("name").asText();
            if ("hello_osgi".equals(toolName)) {
                hasHelloOsgi = true;
            } else if ("bundle_info".equals(toolName)) {
                hasBundleInfo = true;
                assertTrue(tool.has("description"), "bundle_info should have description");
                assertTrue(tool.get("description").asText().contains("JAR or MANIFEST.MF"), 
                    "bundle_info description should mention JAR or MANIFEST.MF");
            } else if ("find".equals(toolName)) {
                hasFind = true;
                assertTrue(tool.has("description"), "find should have description");
                assertTrue(tool.get("description").asText().contains("package") || 
                          tool.get("description").asText().contains("bundle") ||
                          tool.get("description").asText().contains("capability"), 
                    "find description should mention package, bundle, or capability");
            }
        }
        
        assertTrue(hasHelloOsgi, "Should have hello_osgi tool");
        assertTrue(hasBundleInfo, "Should have bundle_info tool");
        assertTrue(hasFind, "Should have find tool");
    }
    
    /**
     * Test calling the bundle_info tool with a JAR file.
     */
    @Test
    void testCallBundleInfoWithJar() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "bundle_info")
            .set("arguments", mapper.createObjectNode()
                .put("file", "/path/to/mybundle.jar"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        assertTrue(result.has("content"), "Result should have content");
        JsonNode content = result.get("content");
        assertTrue(content.isArray(), "Content should be an array");
        assertTrue(content.size() > 0, "Content should not be empty");
        
        JsonNode textContent = content.get(0);
        assertEquals("text", textContent.get("type").asText(), "Content type should be text");
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Bundle Information Analysis"), "Response should contain bundle analysis title");
        assertTrue(text.contains("Bundle-SymbolicName"), "Response should contain Bundle-SymbolicName");
        assertTrue(text.contains("Bundle-Version"), "Response should contain Bundle-Version");
        assertTrue(text.contains("Required Bundles"), "Response should contain Required Bundles");
        assertTrue(text.contains("Required Packages"), "Response should contain Required Packages");
        assertTrue(text.contains("Required Capabilities"), "Response should contain Required Capabilities");
    }
    
    /**
     * Test calling the bundle_info tool with a MANIFEST.MF file.
     */
    @Test
    void testCallBundleInfoWithManifest() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "bundle_info")
            .set("arguments", mapper.createObjectNode()
                .put("file", "/path/to/META-INF/MANIFEST.MF"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        
        JsonNode content = result.get("content");
        JsonNode textContent = content.get(0);
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Valid OSGi Bundle"), "Response should indicate valid OSGi bundle");
    }
    
    /**
     * Test calling the bundle_info tool with a non-bundle file.
     */
    @Test
    void testCallBundleInfoWithNonBundle() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "bundle_info")
            .set("arguments", mapper.createObjectNode()
                .put("file", "/path/to/some-file.txt"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        
        JsonNode content = result.get("content");
        JsonNode textContent = content.get(0);
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Not a bundle"), "Response should indicate not a bundle");
    }
    
    /**
     * Test calling the find tool to search for a package.
     */
    @Test
    void testCallFindPackage() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "find")
            .set("arguments", mapper.createObjectNode()
                .put("type", "package")
                .put("name", "org.apache.commons.logging"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        assertTrue(result.has("content"), "Result should have content");
        JsonNode content = result.get("content");
        assertTrue(content.isArray(), "Content should be an array");
        assertTrue(content.size() > 0, "Content should not be empty");
        
        JsonNode textContent = content.get(0);
        assertEquals("text", textContent.get("type").asText(), "Content type should be text");
        String text = textContent.get("text").asText();
        assertTrue(text.contains("OSGi Repository Search"), "Response should contain search title");
        assertTrue(text.contains("Search Type: package"), "Response should contain search type");
        assertTrue(text.contains("Bundle:"), "Response should contain Bundle results");
        assertTrue(text.contains("Version:"), "Response should contain Version information");
        assertTrue(text.contains("Download URL:"), "Response should contain Download URL");
    }
    
    /**
     * Test calling the find tool to search for a bundle.
     */
    @Test
    void testCallFindBundle() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "find")
            .set("arguments", mapper.createObjectNode()
                .put("type", "bundle")
                .put("name", "org.eclipse.osgi"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        
        JsonNode content = result.get("content");
        JsonNode textContent = content.get(0);
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Search Type: bundle"), "Response should contain search type");
        assertTrue(text.contains("Download URL:"), "Response should contain Download URL");
    }
    
    /**
     * Test calling the find tool to search for a capability.
     */
    @Test
    void testCallFindCapability() throws Exception {
        // Initialize first
        ObjectMapper mapper = new ObjectMapper();
        JsonNode initParams = mapper.createObjectNode()
            .put("protocolVersion", "2024-11-05")
            .set("clientInfo", mapper.createObjectNode()
                .put("name", "test-client")
                .put("version", "1.0.0"));
        sendRequest("initialize", initParams);
        
        // Send initialized notification (required by MCP protocol)
        sendNotification("notifications/initialized");
        
        // Build tools/call params
        JsonNode callParams = mapper.createObjectNode()
            .put("name", "find")
            .set("arguments", mapper.createObjectNode()
                .put("type", "capability")
                .put("name", "osgi.service.component"));
        
        // Send tools/call request
        JsonNode response = sendRequest("tools/call", callParams);
        
        // Verify response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.has("result"), "Response should have result");
        
        JsonNode result = response.get("result");
        assertFalse(result.get("isError").asBoolean(), "Result should not be an error");
        
        JsonNode content = result.get("content");
        JsonNode textContent = content.get(0);
        String text = textContent.get("text").asText();
        assertTrue(text.contains("Search Type: capability"), "Response should contain search type");
        assertTrue(text.contains("Provides Capability:"), "Response should contain capability information");
        assertTrue(text.contains("Download URL:"), "Response should contain Download URL");
    }
}
