
package com.mobilityos.common.event.listener;

import com.mobilityos.common.event.events.DomainEvent;

public interface DomainEventListener<T extends DomainEvent> {

    void handle(T event);
}