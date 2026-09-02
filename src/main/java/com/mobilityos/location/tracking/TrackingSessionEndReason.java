package com.mobilityos.location.tracking;

public enum TrackingSessionEndReason {

    /*
     * Operator intentionally stopped tracking.
     */
    USER_ENDED,

    /*
     * Vehicle operation was handed to another member.
     */
    HANDOVER,

    /*
     * Organization removed the VehicleMember relationship.
     */
    MEMBERSHIP_REVOKED,

    /*
     * Vehicle became inactive or unavailable for operation.
     */
    VEHICLE_DEACTIVATED,

    /*
     * Runtime tracking session became stale and was
     * terminated automatically.
     */
    SESSION_TIMEOUT,

    /*
     * Organization manager explicitly terminated operation.
     */
    ADMIN_TERMINATED,

    /*
     * Only for historical/backfilled rows where the original
     * reason is unavailable.
     */
    UNSPECIFIED
}