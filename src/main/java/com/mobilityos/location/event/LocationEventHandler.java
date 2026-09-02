package com.mobilityos.location.event;

import com.mobilityos.location.observation.LocationObservation;

@FunctionalInterface
public interface LocationEventHandler {

    void handle(LocationObservation observation);
}