package org.jboss.cx.remoting.http.se6;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.http.AbstractHttpChannel;
import org.jboss.cx.remoting.http.HttpProtocolSupport;
import org.jboss.cx.remoting.http.cookie.Cookie;
import org.jboss.cx.remoting.http.cookie.CookieParser;
import org.jboss.cx.remoting.http.spi.AbstractIncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.RemotingHttpChannelContext;
import org.jboss.cx.remoting.http.spi.RemotingHttpServerContext;
import org.jboss.cx.remoting.util.AbstractOutputStreamByteMessageOutput;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.InputStreamByteMessageInput;
import org.jboss.cx.remoting.util.IoUtil;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 *
 */
public final class SunHttpServerChannel extends AbstractHttpChannel implements HttpHandler {

    public SunHttpServerChannel() {
    }

    // Configuration

    private CookieParser cookieParser;

    public CookieParser getCookieParser() {
        return cookieParser;
    }

    public void setCookieParser(final CookieParser cookieParser) {
        this.cookieParser = cookieParser;
    }

    // Dependencies

    private HttpProtocolSupport protocolSupport;
    private RemotingHttpServerContext serverContext;
    private HttpContext httpContext;

    public HttpProtocolSupport getProtocolSupport() {
        return protocolSupport;
    }

    public void setProtocolSupport(final HttpProtocolSupport protocolSupport) {
        this.protocolSupport = protocolSupport;
    }

    public RemotingHttpServerContext getServerContext() {
        return serverContext;
    }

    public void setServerContext(final RemotingHttpServerContext serverContext) {
        this.serverContext = serverContext;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    public void setHttpContext(final HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    // Lifecycle

    public void create() {
        if (serverContext == null) {
            throw new NullPointerException("serverContext is null");
        }
    }

    public void start() {
        httpContext.setHandler(this);
    }

    public void stop() {
        httpContext.setHandler(new HttpHandler() {
            public void handle(final HttpExchange exchange) throws IOException {
                throw new IOException("Context is not available");
            }
        });
    }

    public void destroy() {
        serverContext = null;
        httpContext = null;
    }

    // Implementation

    private final ConcurrentMap<String, RemotingHttpChannelContext> sessions = CollectionUtil.concurrentMap();

    public void handle(final HttpExchange exchange) throws IOException {
        // it could be a non-https exchange (in the case of a separate SSL frontend)
        final boolean secure = "https".equals(exchange.getProtocol());
        final Headers requestHeader = exchange.getRequestHeaders();
        final List<String> cookieHeaders = requestHeader.get("Cookie");
        int parkTimeout = -1;
        String sessionId = null;
        for (String cookieString : cookieHeaders) {
            final List<Cookie> cookies = cookieParser.parseCookie(cookieString);
            for (Cookie cookie : cookies) {
                if ("Park-Timeout".equals(cookie.getName())) {
                    try {
                        parkTimeout = Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        // oh well
                    }
                } else if ("JSESSIONID".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                }
            }
        }
        final boolean needToSetSession;
        RemotingHttpChannelContext context = sessions.get(sessionId);
        final InputStream inputStream = exchange.getRequestBody();
        try {
            final AbstractIncomingHttpMessage incomingMessage = new AbstractIncomingHttpMessage() {
                public ByteMessageInput getMessageData() throws IOException {
                    return new InputStreamByteMessageInput(inputStream, -1);
                }
            };
            if (context == null) {
                needToSetSession = true;
                context = serverContext.processUnsolicitedInboundMessage(incomingMessage);
            } else {
                needToSetSession = false;
                context.processInboundMessage(incomingMessage);
            }
        } finally {
            IoUtil.closeSafely(inputStream);
        }
        if (needToSetSession) {
            final StringBuilder setCookieBuilder = new StringBuilder(60);
            setCookieBuilder.append("JSESSIONID=");
            for (;;) {
                String jsessionid = protocolSupport.generateSessionId();
                if (sessions.putIfAbsent(jsessionid, context) == null) {
                    setCookieBuilder.append(jsessionid);
                    break;
                }
            }
            if (secure) {
                setCookieBuilder.append("; secure");
            }
            exchange.getResponseHeaders().set("Set-Cookie", setCookieBuilder.toString());
        }
        final OutgoingHttpMessage outgoingMessage = context.waitForOutgoingHttpMessage(parkTimeout);
        final OutputStream outputStream = exchange.getResponseBody();
        try {
            outgoingMessage.writeMessageData(new AbstractOutputStreamByteMessageOutput(outputStream) {
                public void commit() throws IOException {
                }
            });
        } finally {
            IoUtil.closeSafely(outputStream);
        }
    }
}
