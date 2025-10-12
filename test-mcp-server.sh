#!/bin/bash
# Test script for the MCP OSGi Server
# This script demonstrates how to interact with the MCP server via JSON-RPC

echo "Testing MCP OSGi Server..."
echo "=========================="
echo ""

# Build the project if needed
if [ ! -f target/mcp-osgi-server-1.0.0-SNAPSHOT.jar ]; then
    echo "Building the project..."
    mvn clean package -q
    echo ""
fi

# Start the server in the background
java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar &
SERVER_PID=$!

# Give the server time to start
sleep 2

# Send test requests
echo "1. Sending initialize request..."
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test-client","version":"1.0.0"}}}' | nc localhost 12345 2>/dev/null || echo "Note: Server uses stdio, not network socket"
echo ""

echo "2. Listing available tools..."
echo '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | nc localhost 12345 2>/dev/null || echo "Note: Server uses stdio, not network socket"
echo ""

echo "3. Calling hello_osgi tool..."
echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"hello_osgi","arguments":{"name":"Test User"}}}' | nc localhost 12345 2>/dev/null || echo "Note: Server uses stdio, not network socket"
echo ""

# Clean up
kill $SERVER_PID 2>/dev/null

echo "=========================="
echo "Test complete!"
echo ""
echo "Note: The MCP server communicates via stdio (standard input/output)."
echo "To use it with an MCP client, configure the client to run:"
echo "  java -jar target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"
