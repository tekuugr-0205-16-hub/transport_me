# WebSocket/STOMP authentication

MobilityOS authenticates realtime sessions on the STOMP `CONNECT` frame.
The SockJS HTTP handshake remains open because browser/SockJS transports may
not be able to attach the normal HTTP Bearer header consistently.

## Connect

Send the normal JWT access token as a STOMP connect header:

```text
Authorization: Bearer <ACCESS_TOKEN>
```

Refresh tokens are not accepted.

## Subscribe

Authenticated sessions may subscribe only to:

```text
/topic/vehicles/{vehicleId}
```

Example:

```text
/topic/vehicles/42
```

## Client publishing

Client `SEND` frames are currently rejected. Live vehicle-location messages
are published only by the backend `RealtimePublisher` after an authorized GPS
ping is accepted by `LocationIngestionService`.

When driver GPS ingestion is later moved from HTTP to STOMP, add a dedicated
`/app/...` destination and enforce VehicleMember authorization there before
allowing that specific client `SEND` flow.
