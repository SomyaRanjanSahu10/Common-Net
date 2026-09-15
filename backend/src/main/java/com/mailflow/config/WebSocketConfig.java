package com.mailflow.config;

import com.mailflow.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.lang.NonNull;

import java.security.Principal;
import java.util.Map;

/**
 * Replaces the Socket.IO setup in server.js:
 *
 *   const io = new Server(server, { cors: {...} });
 *   const userSockets = {};
 *   io.on('connection', socket => {
 *     socket.on('register', uid => { userSockets[uid] = socket.id; });
 *     socket.on('disconnect', () => { ...remove... });
 *   });
 *
 * With STOMP over WebSocket + Spring's SimpUserRegistry, "registering" a
 * socket to a user id is handled automatically once the handshake carries a
 * validated JWT (see JwtHandshakeInterceptor) — Spring then lets us push to
 * a specific user via SimpMessagingTemplate.convertAndSendToUser(userId, ...).
 *
 * Client subscribes to: /user/queue/notifications
 * Server (NotificationService) sends events: new_email, important_email, mention
 * as the "type" field of the payload, replicating the Socket.IO event names.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${mailflow.cors.client-url:http://localhost:3000}")
    private String clientUrl;

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(clientUrl)
                .addInterceptors(new JwtHandshakeInterceptor(jwtUtil))
                .setHandshakeHandler(userIdHandshakeHandler())
                .withSockJS(); // fallback for browsers/proxies that block raw WS
    }

    /**
     * Turns the "userId" handshake attribute (set by JwtHandshakeInterceptor)
     * into the STOMP session's Principal, so
     * SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload)
     * correctly routes to this session — this is the direct equivalent of
     * userSockets[uid] = socket.id in the original server.js.
     */
    private DefaultHandshakeHandler userIdHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(@NonNull ServerHttpRequest request, @NonNull WebSocketHandler wsHandler,
                                               @NonNull Map<String, Object> attributes) {
                Object userId = attributes.get("userId");
                String id = userId != null ? userId.toString() : java.util.UUID.randomUUID().toString();
                return () -> id; // Principal::getName
            }
        };
    }

    /**
     * Reads ?token=<jwt> from the handshake URL (frontend connects as
     * `new SockJS('/ws?token=' + jwtToken)`), validates it, and stashes the
     * user id in the WebSocket session attributes so a
     * ChannelInterceptor/Principal resolver (see WebSocketAuthConfig) can set
     * it as the STOMP Principal — this is what makes
     * convertAndSendToUser(userId, ...) work, replacing userSockets[uid].
     */
    static class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtUtil jwtUtil;
        JwtHandshakeInterceptor(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

        @Override
        public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                        @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            if (query == null) return false;
            String token = null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring("token=".length());
                    break;
                }
            }
            if (token == null || !jwtUtil.isValid(token)) return false;
            attributes.put("userId", jwtUtil.extractUserId(token));
            return true;
        }

        @Override
        public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                    @NonNull WebSocketHandler wsHandler, Exception exception) {
            // no-op
        }
    }
}
