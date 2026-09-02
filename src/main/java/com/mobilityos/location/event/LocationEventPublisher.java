package com.mobilityos.location.event;

import com.mobilityos.location.observation.LocationObservation;

public interface LocationEventPublisher {

    void publish(LocationObservation observation);
}