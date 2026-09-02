package com.mobilityos.location.live;

import com.mobilityos.location.observation.LocationObservation;

public interface VehicleLiveStateStore {

    /*
     * Atomically verifies tracking runtime authority,
     * compares the sequence number, and if newer:
     *
     * - advances runtime sequence
     * - updates live coordinates/metadata
     * - updates GEO position
     *
     * A delayed observation must never overwrite
     * a newer live observation.
     */
    LiveObservationResult applyForLiveState(
            LocationObservation observation
    );
}