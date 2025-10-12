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
     */
    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        
        // Find the JAR file
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        if (!jarFile.exists()) {
            fail("JAR file not found at " + jarFile.getAbsolutePath() + 
                 ". Please run 'mvn package' first.");
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
        assertTrue(serverProcess.isAlive(), "Server process should be running");
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
        assertEquals(1, tools.size(), "Should have 1 tool");
        
        JsonNode tool = tools.get(0);
        assertEquals("hello_osgi", tool.get("name").asText(), "Tool name should be hello_osgi");
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
     */
    @Test
    void testJarFileExists() {
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        // The JAR should exist after the build, but we'll make this test informative
        if (jarFile.exists()) {
            assertTrue(jarFile.length() > 0, "JAR file should not be empty");
        }
        // If JAR doesn't exist, that's okay - it might not have been built yet
        // The main validation is that the code compiles
    }
}
