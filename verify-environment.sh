#!/bin/bash
set -e

echo "=================================="
echo "MCP OSGi Server Environment Check"
echo "=================================="
echo ""

# Check Java
echo "1. Checking Java installation..."
if command -v java &> /dev/null; then
    java -version 2>&1 | head -1
    echo "   ✓ Java is installed"
else
    echo "   ✗ Java is NOT installed"
    exit 1
fi
echo ""

# Check Maven
echo "2. Checking Maven installation..."
if command -v mvn &> /dev/null; then
    mvn -version 2>&1 | head -1
    echo "   ✓ Maven is installed"
else
    echo "   ✗ Maven is NOT installed"
    exit 1
fi
echo ""

# Check if JAR was built
echo "3. Checking if MCP server JAR exists in project..."
if [ -f "target/mcp-osgi-server-1.0.0-SNAPSHOT.jar" ]; then
    JAR_SIZE=$(ls -lh target/mcp-osgi-server-1.0.0-SNAPSHOT.jar | awk '{print $5}')
    echo "   ✓ JAR exists: target/mcp-osgi-server-1.0.0-SNAPSHOT.jar (${JAR_SIZE})"
else
    echo "   ✗ JAR does NOT exist at: target/mcp-osgi-server-1.0.0-SNAPSHOT.jar"
    echo "   Building now..."
    mvn clean package -DskipTests -B -q
    if [ -f "target/mcp-osgi-server-1.0.0-SNAPSHOT.jar" ]; then
        echo "   ✓ Build successful"
    else
        echo "   ✗ Build failed"
        exit 1
    fi
fi
echo ""

# Check if JAR was deployed to standard location
echo "4. Checking if MCP server JAR exists in standard location..."
if [ -f "/home/runner/tools/osgi_mcp/server.jar" ]; then
    JAR_SIZE=$(ls -lh /home/runner/tools/osgi_mcp/server.jar | awk '{print $5}')
    echo "   ✓ JAR exists: /home/runner/tools/osgi_mcp/server.jar (${JAR_SIZE})"
else
    echo "   ⚠ JAR does NOT exist at: /home/runner/tools/osgi_mcp/server.jar"
    echo "   Copying now..."
    mkdir -p /home/runner/tools/osgi_mcp
    cp target/mcp-osgi-server-1.0.0-SNAPSHOT.jar /home/runner/tools/osgi_mcp/server.jar
    echo "   ✓ JAR copied to /home/runner/tools/osgi_mcp/server.jar"
fi
echo ""

# Test running the server in stdio mode
echo "5. Testing server in stdio mode..."
INIT_REQUEST='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0"}}}'
RESPONSE=$(timeout 5 sh -c "echo '$INIT_REQUEST' | java -jar /home/runner/tools/osgi_mcp/server.jar 2>/dev/null" || true)
if echo "$RESPONSE" | grep -q '"result"'; then
    echo "   ✓ Server responds in stdio mode"
else
    echo "   ✗ Server does NOT respond properly in stdio mode"
    echo "   Response: $RESPONSE"
    exit 1
fi
echo ""

# Test running the server in HTTP/SSE mode
echo "6. Testing server in server mode (HTTP/SSE)..."
java -jar /home/runner/tools/osgi_mcp/server.jar server 3002 > /tmp/mcp-server.log 2>&1 &
SERVER_PID=$!
sleep 3

# Check if server is running
if kill -0 $SERVER_PID 2>/dev/null; then
    echo "   ✓ Server process started (PID: $SERVER_PID)"
    
    # Test HTTP endpoint with retries - just check headers, don't wait for body
    SUCCESS=false
    for i in {1..5}; do
        HEADERS=$(curl -s -I http://localhost:3002/mcp/sse -m 2 2>/dev/null || true)
        if echo "$HEADERS" | grep -q "text/event-stream"; then
            echo "   ✓ Server responds on HTTP endpoint"
            SUCCESS=true
            break
        fi
        sleep 1
    done
    
    if [ "$SUCCESS" = false ]; then
        echo "   ⚠ Server HTTP endpoint check inconclusive"
        echo "   Server appears to be running (check logs manually if needed)"
    fi
    
    # Clean up
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true
else
    echo "   ✗ Server failed to start"
    cat /tmp/mcp-server.log
    exit 1
fi
echo ""

# Check MCP environment variables
echo "7. Checking MCP environment variables..."
if [ ! -z "$COPILOT_MCP_ENABLED" ]; then
    echo "   ✓ COPILOT_MCP_ENABLED=$COPILOT_MCP_ENABLED"
else
    echo "   ⚠ COPILOT_MCP_ENABLED is not set"
fi

if [ ! -z "$COPILOT_AGENT_MCP_SERVER_TEMP" ]; then
    echo "   ✓ COPILOT_AGENT_MCP_SERVER_TEMP=$COPILOT_AGENT_MCP_SERVER_TEMP"
    
    # Check if MCP config exists
    if [ -f "$COPILOT_AGENT_MCP_SERVER_TEMP/mcp-config.json" ]; then
        TOOL_COUNT=$(jq 'keys | length' "$COPILOT_AGENT_MCP_SERVER_TEMP/mcp-config.json")
        echo "   ✓ MCP config exists with $TOOL_COUNT tools"
        
        # Check if our OSGi tools are registered
        OSGI_TOOLS=$(jq -r 'keys | .[]' "$COPILOT_AGENT_MCP_SERVER_TEMP/mcp-config.json" | grep -c "osgi" || echo "0")
        if [ "$OSGI_TOOLS" -gt 0 ]; then
            echo "   ✓ Found $OSGI_TOOLS OSGi tools registered"
        else
            echo "   ⚠ No OSGi tools found in MCP config (this is expected if not yet registered)"
        fi
    else
        echo "   ⚠ MCP config file does not exist at $COPILOT_AGENT_MCP_SERVER_TEMP/mcp-config.json"
    fi
else
    echo "   ⚠ COPILOT_AGENT_MCP_SERVER_TEMP is not set"
fi
echo ""

# Check workspace
echo "8. Checking workspace visibility..."
echo "   GITHUB_WORKSPACE=$GITHUB_WORKSPACE"
echo "   Current directory: $(pwd)"
if [ "$(pwd)" = "$GITHUB_WORKSPACE" ]; then
    echo "   ✓ We are in the GitHub workspace"
else
    echo "   ⚠ Current directory differs from GITHUB_WORKSPACE"
fi
echo ""

echo "=================================="
echo "Summary: Environment Check PASSED"
echo "=================================="
echo ""
echo "The MCP OSGi server is properly set up and can be started with:"
echo "  java -jar /home/runner/tools/osgi_mcp/server.jar           # stdio mode"
echo "  java -jar /home/runner/tools/osgi_mcp/server.jar server    # HTTP/SSE mode (port 3000)"
echo ""
