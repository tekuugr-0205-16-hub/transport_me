package com.mobilityos.location.observation;

public enum LocationObservationIngestionResult {

    APPLIED,

    DUPLICATE_CURRENT,

    STALE_SEQUENCE,

    REJECTED_QUALITY,

    NO_ACTIVE_SESSION,

    SESSION_MISMATCH,

    USER_MISMATCH,

    DEVICE_MISMATCH
}