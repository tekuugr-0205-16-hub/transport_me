
package com.mobilityos.common.event.publisher;

import com.mobilityos.common.event.events.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}