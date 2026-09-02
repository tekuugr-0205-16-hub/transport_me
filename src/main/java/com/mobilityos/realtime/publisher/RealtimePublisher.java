package com.mobilityos.realtime.publisher;

import com.mobilityos.location.dto.LiveLocationResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishLocationUpdate(Long vehicleId, LiveLocationResponse location) {
        messagingTemplate.convertAndSend("/topic/vehicles/" + vehicleId, location);
    }
}
