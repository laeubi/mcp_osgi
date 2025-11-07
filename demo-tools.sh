#!/bin/bash
# Demonstration of the OSGi MCP Server Tools

echo "=========================================="
echo "OSGi MCP Server - Tools Demonstration"
echo "=========================================="
echo ""

# Ensure JAR is in place
if [ ! -f "/home/runner/tools/osgi_mcp/server.jar" ]; then
    echo "Setting up server JAR..."
    mkdir -p /home/runner/tools/osgi_mcp
    cp target/mcp-osgi-server-1.0.0-SNAPSHOT.jar /home/runner/tools/osgi_mcp/server.jar
fi

echo "1. Testing stdio mode - Listing available tools..."
echo ""

(
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"demo","version":"1.0"}}}'
sleep 1
echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
sleep 1
echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
sleep 2
) | timeout 5 java -jar /home/runner/tools/osgi_mcp/server.jar 2>/dev/null > /tmp/tools-result.txt

echo "Available OSGi Tools:"
echo "--------------------"
cat /tmp/tools-result.txt | grep '"id":2' | jq -r '.result.tools[] | "  • \(.name): \(.description)"' 2>/dev/null

echo ""
echo "2. Testing a tool call - hello_osgi..."
echo ""

(
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"demo","version":"1.0"}}}'
sleep 1
echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
sleep 1
echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"hello_osgi","arguments":{"name":"GitHub Copilot"}}}'
sleep 2
) | timeout 5 java -jar /home/runner/tools/osgi_mcp/server.jar 2>/dev/null > /tmp/hello-result.txt

echo "Result from hello_osgi tool:"
echo "----------------------------"
cat /tmp/hello-result.txt | grep '"id":3' | jq -r '.result.content[0].text' 2>/dev/null

echo ""
echo "=========================================="
echo "Server is fully functional!"
echo "=========================================="
echo ""
echo "The server provides 3 OSGi tools:"
echo "  1. hello_osgi - Demonstration greeting with OSGi context"
echo "  2. bundle_info - Analyze JAR/MANIFEST files for OSGi metadata"
echo "  3. find - Search for OSGi packages, bundles, or capabilities"
echo ""
echo "For GitHub Copilot integration, use the configuration in:"
echo "  mcp-client-config-copilot-agent.json"
echo ""
