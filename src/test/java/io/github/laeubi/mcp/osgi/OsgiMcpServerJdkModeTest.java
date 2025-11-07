package io.github.laeubi.mcp.osgi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the MCP OSGi Server in jdkserver mode.
 * 
 * This test class verifies that the server can start in jdkserver mode
 * using JDK's built-in HTTP server and respond to HTTP requests on the configured port.
 */
class OsgiMcpServerJdkModeTest {
    
    private Process serverProcess;
    private static final int TEST_PORT = 3458;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;
    
    /**
     * Set up for each test - starts the MCP server in jdkserver mode.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Find the JAR file
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        if (!jarFile.exists()) {
            fail("JAR file not found at " + jarFile.getAbsolutePath() + 
                 ". Please run 'mvn package' first, or use 'mvn verify' to build and test.");
        }
        
        // Start the server process in jdkserver mode
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java",
            "-jar",
            jarFile.getAbsolutePath(),
            "jdkserver",
            String.valueOf(TEST_PORT)
        );
        processBuilder.redirectErrorStream(false);
        
        serverProcess = processBuilder.start();
        
        // Give the server time to start
        Thread.sleep(2000);
        
        // Verify the process is running
        if (!serverProcess.isAlive()) {
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
    }
    
    /**
     * Test that the server starts successfully in jdkserver mode.
     */
    @Test
    void testJdkServerModeStartup() throws Exception {
        // Verify the process is still alive after setup
        assertTrue(serverProcess.isAlive(), "Server process should be running");
        
        // Give it a moment to fully initialize
        Thread.sleep(500);
        
        // Verify still running
        assertTrue(serverProcess.isAlive(), "Server process should still be running");
    }
    
    /**
     * Test that the server responds to HTTP requests on the SSE endpoint.
     */
    @Test
    void testJdkServerHttpEndpointAccessible() throws Exception {
        // Try to access the SSE endpoint
        URL url = new URL(BASE_URL + "/mcp/sse");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try {
            int responseCode = connection.getResponseCode();
            // SSE endpoint should be accessible (might return various codes depending on implementation)
            // We just want to verify the server is responding
            assertTrue(responseCode > 0, "Server should respond to HTTP requests");
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Test that the server can be started with the jdkserver parameter.
     */
    @Test
    void testJdkServerModeWithPort() throws Exception {
        // This test verifies the server can parse arguments correctly
        // by checking the process is running (already done in setUp with custom port)
        assertTrue(serverProcess.isAlive(), "Server should start with custom port");
    }
    
    /**
     * Test that invalid port causes proper error handling with jdkserver mode.
     */
    @Test
    void testJdkServerModeWithInvalidPort() throws Exception {
        // Stop the current server first
        tearDown();
        
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java",
            "-jar",
            jarFile.getAbsolutePath(),
            "jdkserver",
            "invalid-port"
        );
        processBuilder.redirectErrorStream(true);
        
        Process testProcess = processBuilder.start();
        
        // Wait for the process to exit
        boolean exited = testProcess.waitFor(5, TimeUnit.SECONDS);
        assertTrue(exited, "Process should exit when given invalid port");
        
        // Check that it exited with an error code
        assertEquals(1, testProcess.exitValue(), "Should exit with error code 1 for invalid port");
        
        testProcess.destroy();
    }
    
    /**
     * Test that the server accepts both "jdkserver" and "JDKSERVER" as the mode parameter.
     */
    @Test
    void testJdkServerModeCaseInsensitive() throws Exception {
        // Stop the current server first
        tearDown();
        
        File jarFile = new File("target/mcp-osgi-server-1.0.0-SNAPSHOT.jar");
        ProcessBuilder processBuilder = new ProcessBuilder(
            "java",
            "-jar",
            jarFile.getAbsolutePath(),
            "JDKSERVER",  // Test uppercase
            "3459"
        );
        processBuilder.redirectErrorStream(false);
        
        serverProcess = processBuilder.start();
        Thread.sleep(2000);
        
        assertTrue(serverProcess.isAlive(), "Server should start with 'JDKSERVER' (uppercase) parameter");
    }
}
