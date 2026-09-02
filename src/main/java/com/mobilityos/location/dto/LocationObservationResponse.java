package com.mobilityos.location.dto;

import com.mobilityos.location.observation.LocationObservationIngestionResult;

public record LocationObservationResponse(

        LocationObservationIngestionResult result

) {
}