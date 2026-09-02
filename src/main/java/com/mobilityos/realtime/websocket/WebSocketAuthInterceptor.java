package com.mobilityos.realtime.websocket;

import com.mobilityos.auth.security.jwt.JwtClaims;
import com.mobilityos.auth.security.jwt.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP sessions with the same JWT ACCESS tokens used by HTTP.
 *
 * The HTTP /ws handshake remains open because SockJS/browser transports cannot
 * reliably attach a Bearer header to every handshake request. Authentication
 * therefore happens on the STOMP CONNECT frame instead.
 *
 * Clients may subscribe only to vehicle-location topics. Client SEND frames
 * are intentionally rejected for now: vehicle location events are published
 * only by the trusted backend RealtimePublisher.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String AUTHORIZATION_LOWERCASE = "authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern VEHICLE_TOPIC = Pattern.compile("^/topic/vehicles/\\d+$");

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            authenticateConnect(accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            requireAuthenticated(accessor);
            requireVehicleTopic(accessor.getDestination());
            return message;
        }

        if (StompCommand.SEND.equals(command)) {
            throw new AccessDeniedException("Client STOMP SEND frames are not allowed");
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION);
        if (header == null) {
            header = accessor.getFirstNativeHeader(AUTHORIZATION_LOWERCASE);
        }

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Missing WebSocket access token");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || !jwtService.isAccessTokenValid(token)) {
            throw new BadCredentialsException("Invalid WebSocket access token");
        }

        JwtClaims claims = jwtService.parseAccessToken(token);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                claims.userId(), null, List.of()
        ));
    }

    private void requireAuthenticated(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("Authenticated WebSocket session required");
        }
    }

    private void requireVehicleTopic(String destination) {
        if (destination == null || !VEHICLE_TOPIC.matcher(destination).matches()) {
            throw new AccessDeniedException("Subscription destination is not allowed");
        }
    }
}
