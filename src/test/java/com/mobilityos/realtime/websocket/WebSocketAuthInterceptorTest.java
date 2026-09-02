package com.mobilityos.realtime.websocket;

import com.mobilityos.auth.security.jwt.JwtClaims;
import com.mobilityos.auth.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    @Test
    void connectWithValidAccessTokenAuthenticatesSession() {
        JwtService jwtService = mock(JwtService.class);
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtService);
        String token = "valid-access-token";

        when(jwtService.isAccessTokenValid(token)).thenReturn(true);
        when(jwtService.parseAccessToken(token)).thenReturn(new JwtClaims(42L, "0911000000"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = message(accessor);

        interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));

        StompHeaderAccessor after = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertNotNull(after.getUser());
        assertEquals("42", after.getUser().getName());
    }

    @Test
    void connectWithoutAccessTokenIsRejected() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(mock(JwtService.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);

        assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void connectWithRefreshOrInvalidTokenIsRejected() {
        JwtService jwtService = mock(JwtService.class);
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtService);
        String token = "refresh-token";
        when(jwtService.isAccessTokenValid(token)).thenReturn(false);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);

        assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void authenticatedUserCanSubscribeToVehicleTopic() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(mock(JwtService.class));
        StompHeaderAccessor accessor = authenticatedAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/vehicles/123");

        interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class));
    }

    @Test
    void unauthenticatedSubscriptionIsRejected() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(mock(JwtService.class));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/vehicles/123");
        accessor.setLeaveMutable(true);

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void authenticatedSubscriptionOutsideVehicleTopicsIsRejected() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(mock(JwtService.class));
        StompHeaderAccessor accessor = authenticatedAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/secrets");

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)));
    }

    @Test
    void clientSendFrameIsRejected() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(mock(JwtService.class));
        StompHeaderAccessor accessor = authenticatedAccessor(StompCommand.SEND);
        accessor.setDestination("/topic/vehicles/123");

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), mock(org.springframework.messaging.MessageChannel.class)));
    }

    private static StompHeaderAccessor authenticatedAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                42L, null, java.util.List.of()
        ));
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
