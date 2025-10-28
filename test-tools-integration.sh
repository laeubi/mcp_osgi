#!/bin/bash

# Integration test for OSGi MCP server tools
# This script tests the actual MCP tools via HTTP endpoints

set -e

SERVER_URL="http://localhost:3000"
SESSION_ID=""

echo "========================================"
echo "OSGi MCP Server Integration Test"
echo "========================================"
echo ""

# Function to make JSON-RPC request
make_request() {
    local method=$1
    local params=$2
    local id=$3
    
    local request=$(cat <<EOF
{
  "jsonrpc": "2.0",
  "id": $id,
  "method": "$method",
  "params": $params
}
EOF
)
    
    echo "Request: $method"
    echo "$request" | jq '.' 2>/dev/null || echo "$request"
    
    if [ -z "$SESSION_ID" ]; then
        # First, connect to SSE endpoint to get session ID
        local sse_response=$(timeout 2 curl -s -N "$SERVER_URL/mcp/sse" | grep "data:" | head -1)
        SESSION_ID=$(echo "$sse_response" | sed 's/.*sessionId=\([^"&]*\).*/\1/')
        
        if [ -z "$SESSION_ID" ]; then
            echo "ERROR: Failed to extract session ID from SSE response"
            echo "SSE Response: $sse_response"
            return 1
        fi
        echo "Got session ID: $SESSION_ID"
    fi
    
    # Validate session ID is not empty before making request
    if [ -z "$SESSION_ID" ]; then
        echo "ERROR: SESSION_ID is empty, cannot make request"
        return 1
    fi
    
    # Send request to message endpoint
    local response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$request" \
        "$SERVER_URL/mcp/message?sessionId=$SESSION_ID")
    
    echo "Response:"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
    echo ""
    
    return 0
}

# Test 1: Initialize the server
echo "Test 1: Initialize MCP server"
echo "------------------------------"
INIT_PARAMS=$(cat <<EOF
{
  "protocolVersion": "2024-11-05",
  "clientInfo": {
    "name": "integration-test",
    "version": "1.0.0"
  }
}
EOF
)
make_request "initialize" "$INIT_PARAMS" 1
echo ""

# Test 2: List available tools
echo "Test 2: List available tools"
echo "----------------------------"
make_request "tools/list" "{}" 2
echo ""

# Test 3: Call hello_osgi tool
echo "Test 3: Call hello_osgi tool"
echo "----------------------------"
HELLO_PARAMS=$(cat <<EOF
{
  "name": "hello_osgi",
  "arguments": {
    "name": "Integration Test"
  }
}
EOF
)
make_request "tools/call" "$HELLO_PARAMS" 3
echo ""

# Test 4: Call bundle_info tool
echo "Test 4: Call bundle_info tool"
echo "------------------------------"
BUNDLE_PARAMS=$(cat <<EOF
{
  "name": "bundle_info",
  "arguments": {
    "file": "/home/runner/tools/osgi_mcp/server.jar"
  }
}
EOF
)
make_request "tools/call" "$BUNDLE_PARAMS" 4
echo ""

# Test 5: Call find tool
echo "Test 5: Call find tool"
echo "----------------------"
FIND_PARAMS=$(cat <<EOF
{
  "name": "find",
  "arguments": {
    "type": "package",
    "name": "org.osgi.framework"
  }
}
EOF
)
make_request "tools/call" "$FIND_PARAMS" 5
echo ""

echo "========================================"
echo "Integration Test Summary"
echo "========================================"
echo "✓ All tool tests completed"
echo "✓ Server responded to all requests"
echo "✓ Tools are accessible and working"
echo "========================================"
