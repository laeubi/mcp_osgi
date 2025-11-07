package io.github.laeubi.mcp.osgi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Wrapper that adapts JDK's HttpServer to work with HttpServletSseServerTransportProvider.
 * 
 * This class bridges the gap between the servlet-based MCP transport and JDK's built-in HTTP server,
 * allowing the MCP server to run without external dependencies like Jetty.
 */
public class JdkHttpServerWrapper {
    
    private static final Logger logger = LoggerFactory.getLogger(JdkHttpServerWrapper.class);
    
    private final HttpServer httpServer;
    private final HttpServletSseServerTransportProvider transportProvider;
    
    public JdkHttpServerWrapper(int port, HttpServletSseServerTransportProvider transportProvider) throws IOException {
        this.transportProvider = transportProvider;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.httpServer.setExecutor(Executors.newCachedThreadPool());
        
        // Register handler for all /mcp/* paths
        this.httpServer.createContext("/mcp", new ServletAdapter(transportProvider));
    }
    
    public void start() {
        httpServer.start();
        logger.info("JDK HTTP server started");
    }
    
    public void stop() {
        httpServer.stop(0);
        if (transportProvider != null) {
            transportProvider.destroy();
        }
        logger.info("JDK HTTP server stopped");
    }
    
    public void join() throws InterruptedException {
        // Keep running until interrupted
        Thread.currentThread().join();
    }
    
    /**
     * Adapter that converts HttpExchange to HttpServletRequest/Response
     */
    private static class ServletAdapter implements HttpHandler {
        private final HttpServletSseServerTransportProvider servlet;
        
        ServletAdapter(HttpServletSseServerTransportProvider servlet) {
            this.servlet = servlet;
        }
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpServletRequestAdapter request = new HttpServletRequestAdapter(exchange);
            HttpServletResponseAdapter response = new HttpServletResponseAdapter(exchange);
            
            try {
                servlet.service(request, response);
            } catch (ServletException e) {
                logger.error("Servlet error", e);
                response.sendError(500, "Internal server error");
            }
        }
    }
    
    /**
     * Adapter from HttpExchange to HttpServletRequest
     */
    private static class HttpServletRequestAdapter implements HttpServletRequest {
        private final HttpExchange exchange;
        private final Map<String, Object> attributes = new HashMap<>();
        
        HttpServletRequestAdapter(HttpExchange exchange) {
            this.exchange = exchange;
        }
        
        @Override
        public String getMethod() {
            return exchange.getRequestMethod();
        }
        
        @Override
        public String getRequestURI() {
            return exchange.getRequestURI().getPath();
        }
        
        @Override
        public String getQueryString() {
            return exchange.getRequestURI().getQuery();
        }
        
        @Override
        public String getHeader(String name) {
            return exchange.getRequestHeaders().getFirst(name);
        }
        
        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = exchange.getRequestHeaders().get(name);
            return values != null ? Collections.enumeration(values) : Collections.emptyEnumeration();
        }
        
        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(exchange.getRequestHeaders().keySet());
        }
        
        @Override
        public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                private final InputStream in = exchange.getRequestBody();
                
                @Override
                public int read() throws IOException {
                    return in.read();
                }
                
                @Override
                public boolean isFinished() {
                    try {
                        return in.available() == 0;
                    } catch (IOException e) {
                        return true;
                    }
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setReadListener(ReadListener readListener) {
                    // Not implemented for now
                }
            };
        }
        
        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        }
        
        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }
        
        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
        
        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }
        
        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }
        
        @Override
        public String getRemoteAddr() {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        
        @Override
        public AsyncContext getAsyncContext() {
            return new AsyncContextAdapter(exchange);
        }
        
        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            return new AsyncContextAdapter(exchange);
        }
        
        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
            return new AsyncContextAdapter(exchange);
        }
        
        @Override
        public boolean isAsyncStarted() {
            return false;
        }
        
        @Override
        public boolean isAsyncSupported() {
            return true;
        }
        
        // Minimal implementations for other required methods
        @Override public String getAuthType() { return null; }
        @Override public Cookie[] getCookies() { return new Cookie[0]; }
        @Override public long getDateHeader(String name) { return -1; }
        @Override public int getIntHeader(String name) { return -1; }
        @Override public String getPathInfo() { return null; }
        @Override public String getPathTranslated() { return null; }
        @Override public String getContextPath() { return ""; }
        @Override public String getRemoteUser() { return null; }
        @Override public boolean isUserInRole(String role) { return false; }
        @Override public Principal getUserPrincipal() { return null; }
        @Override public String getRequestedSessionId() { return null; }
        @Override public StringBuffer getRequestURL() { return new StringBuffer("http://localhost" + getRequestURI()); }
        @Override public String getServletPath() { return "/mcp"; }
        @Override public HttpSession getSession(boolean create) { return null; }
        @Override public HttpSession getSession() { return null; }
        @Override public String changeSessionId() { return null; }
        @Override public boolean isRequestedSessionIdValid() { return false; }
        @Override public boolean isRequestedSessionIdFromCookie() { return false; }
        @Override public boolean isRequestedSessionIdFromURL() { return false; }
        @Override public boolean isRequestedSessionIdFromUrl() { return false; }
        @Override public boolean authenticate(HttpServletResponse response) { return false; }
        @Override public void login(String username, String password) { }
        @Override public void logout() { }
        @Override public Collection<Part> getParts() { return Collections.emptyList(); }
        @Override public Part getPart(String name) { return null; }
        @Override public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) { return null; }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public void setCharacterEncoding(String env) { }
        @Override public int getContentLength() { return -1; }
        @Override public long getContentLengthLong() { return -1L; }
        @Override public String getContentType() { return exchange.getRequestHeaders().getFirst("Content-Type"); }
        @Override public String getParameter(String name) { return null; }
        @Override public Enumeration<String> getParameterNames() { return Collections.emptyEnumeration(); }
        @Override public String[] getParameterValues(String name) { return null; }
        @Override public Map<String, String[]> getParameterMap() { return Collections.emptyMap(); }
        @Override public String getProtocol() { return exchange.getProtocol(); }
        @Override public String getScheme() { return "http"; }
        @Override public String getServerName() { return exchange.getLocalAddress().getHostName(); }
        @Override public int getServerPort() { return exchange.getLocalAddress().getPort(); }
        @Override public String getRemoteHost() { return exchange.getRemoteAddress().getHostName(); }
        @Override public Locale getLocale() { return Locale.getDefault(); }
        @Override public Enumeration<Locale> getLocales() { return Collections.enumeration(Collections.singletonList(Locale.getDefault())); }
        @Override public boolean isSecure() { return false; }
        @Override public RequestDispatcher getRequestDispatcher(String path) { return null; }
        @Override public String getRealPath(String path) { return null; }
        @Override public int getRemotePort() { return exchange.getRemoteAddress().getPort(); }
        @Override public String getLocalName() { return exchange.getLocalAddress().getHostName(); }
        @Override public String getLocalAddr() { return exchange.getLocalAddress().getAddress().getHostAddress(); }
        @Override public int getLocalPort() { return exchange.getLocalAddress().getPort(); }
        @Override public ServletContext getServletContext() { return null; }
        @Override public DispatcherType getDispatcherType() { return DispatcherType.REQUEST; }
        @Override public HttpServletMapping getHttpServletMapping() { return null; }
        @Override public PushBuilder newPushBuilder() { return null; }
        @Override public Map<String, String> getTrailerFields() { return Collections.emptyMap(); }
        @Override public boolean isTrailerFieldsReady() { return true; }
    }
    
    /**
     * Adapter from HttpExchange to HttpServletResponse
     */
    private static class HttpServletResponseAdapter implements HttpServletResponse {
        private final HttpExchange exchange;
        private final PrintWriter writer;
        private int statusCode = 200;
        private boolean committed = false;
        
        HttpServletResponseAdapter(HttpExchange exchange) {
            this.exchange = exchange;
            this.writer = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8));
        }
        
        @Override
        public void setStatus(int sc) {
            this.statusCode = sc;
        }
        
        @Override
        public int getStatus() {
            return statusCode;
        }
        
        @Override
        public void setHeader(String name, String value) {
            exchange.getResponseHeaders().set(name, value);
        }
        
        @Override
        public void addHeader(String name, String value) {
            exchange.getResponseHeaders().add(name, value);
        }
        
        @Override
        public void setContentType(String type) {
            setHeader("Content-Type", type);
        }
        
        @Override
        public String getContentType() {
            return exchange.getResponseHeaders().getFirst("Content-Type");
        }
        
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!committed) {
                exchange.sendResponseHeaders(statusCode, 0);
                committed = true;
            }
            
            return new ServletOutputStream() {
                private final OutputStream out = exchange.getResponseBody();
                
                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                }
                
                @Override
                public void flush() throws IOException {
                    out.flush();
                }
                
                @Override
                public void close() throws IOException {
                    out.close();
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // Not implemented
                }
            };
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
            if (!committed) {
                exchange.sendResponseHeaders(statusCode, 0);
                committed = true;
            }
            return writer;
        }
        
        @Override
        public void flushBuffer() throws IOException {
            if (!committed) {
                exchange.sendResponseHeaders(statusCode, 0);
                committed = true;
            }
            writer.flush();
        }
        
        @Override
        public void sendError(int sc, String msg) throws IOException {
            setStatus(sc);
            if (!committed) {
                byte[] errorMsg = msg.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(sc, errorMsg.length);
                exchange.getResponseBody().write(errorMsg);
                committed = true;
            }
        }
        
        @Override
        public void sendError(int sc) throws IOException {
            sendError(sc, "Error " + sc);
        }
        
        @Override
        public boolean isCommitted() {
            return committed;
        }
        
        // Minimal implementations for other required methods
        @Override public void setStatus(int sc, String sm) { setStatus(sc); }
        @Override public void addCookie(Cookie cookie) { }
        @Override public boolean containsHeader(String name) { return exchange.getResponseHeaders().containsKey(name); }
        @Override public String encodeURL(String url) { return url; }
        @Override public String encodeRedirectURL(String url) { return url; }
        @Override public String encodeRedirectUrl(String url) { return url; }
        @Override public String encodeUrl(String url) { return url; }
        @Override public void sendRedirect(String location) throws IOException { setHeader("Location", location); setStatus(302); }
        @Override public void setDateHeader(String name, long date) { }
        @Override public void addDateHeader(String name, long date) { }
        @Override public void setIntHeader(String name, int value) { setHeader(name, String.valueOf(value)); }
        @Override public void addIntHeader(String name, int value) { addHeader(name, String.valueOf(value)); }
        @Override public String getHeader(String name) { return exchange.getResponseHeaders().getFirst(name); }
        @Override public Collection<String> getHeaders(String name) { return exchange.getResponseHeaders().get(name); }
        @Override public Collection<String> getHeaderNames() { return exchange.getResponseHeaders().keySet(); }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public void setCharacterEncoding(String charset) { }
        @Override public void setContentLength(int len) { }
        @Override public void setContentLengthLong(long len) { }
        @Override public void setBufferSize(int size) { }
        @Override public int getBufferSize() { return 8192; }
        @Override public void resetBuffer() { }
        @Override public void reset() { }
        @Override public void setLocale(Locale loc) { }
        @Override public Locale getLocale() { return Locale.getDefault(); }
        @Override public Supplier<Map<String, String>> getTrailerFields() { return Collections::emptyMap; }
        @Override public void setTrailerFields(Supplier<Map<String, String>> supplier) { }
    }
    
    /**
     * Minimal AsyncContext implementation for SSE support
     */
    private static class AsyncContextAdapter implements AsyncContext {
        private final HttpExchange exchange;
        private final List<AsyncListener> listeners = new ArrayList<>();
        private volatile boolean completed = false;
        
        AsyncContextAdapter(HttpExchange exchange) {
            this.exchange = exchange;
        }
        
        @Override
        public ServletRequest getRequest() {
            return new HttpServletRequestAdapter(exchange);
        }
        
        @Override
        public ServletResponse getResponse() {
            return new HttpServletResponseAdapter(exchange);
        }
        
        @Override
        public boolean hasOriginalRequestAndResponse() {
            return true;
        }
        
        @Override
        public void dispatch() {
            // Not implemented
        }
        
        @Override
        public void dispatch(String path) {
            // Not implemented
        }
        
        @Override
        public void dispatch(ServletContext context, String path) {
            // Not implemented
        }
        
        @Override
        public void complete() {
            if (!completed) {
                completed = true;
                try {
                    exchange.getResponseBody().close();
                } catch (IOException e) {
                    logger.error("Error closing response", e);
                }
            }
        }
        
        @Override
        public void start(Runnable run) {
            new Thread(run).start();
        }
        
        @Override
        public void addListener(AsyncListener listener) {
            listeners.add(listener);
        }
        
        @Override
        public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
            listeners.add(listener);
        }
        
        @Override
        public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
        
        @Override
        public void setTimeout(long timeout) {
            // Not implemented
        }
        
        @Override
        public long getTimeout() {
            return 0;
        }
    }
}
