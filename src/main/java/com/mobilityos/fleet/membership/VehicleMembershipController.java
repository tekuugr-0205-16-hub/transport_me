package com.mobilityos.fleet.membership;

import com.mobilityos.fleet.membership.dto.InvitationResponse;
import com.mobilityos.fleet.membership.dto.InviteMemberRequest;
import com.mobilityos.fleet.membership.dto.VehicleMemberResponse;
import com.mobilityos.fleet.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vehicles")
public class VehicleMembershipController {

    private final VehicleMembershipService vehicleMembershipService;

    public VehicleMembershipController(
            VehicleMembershipService vehicleMembershipService
    ) {
        this.vehicleMembershipService = vehicleMembershipService;
    }

    // =========================================================
    // CURRENT USER'S OPERATIONAL VEHICLES
    // =========================================================

    @GetMapping("/my")
    public ResponseEntity<List<VehicleResponse>> getMyVehicles() {

        List<VehicleResponse> vehicles =
                vehicleMembershipService.getVehiclesForUser(
                        currentUserId()
                );

        return ResponseEntity.ok(vehicles);
    }

    // =========================================================
    // INVITE VEHICLE MEMBER
    // =========================================================

    @PostMapping("/{vehicleId}/members/invite")
    public ResponseEntity<InvitationResponse> inviteMember(
            @PathVariable Long vehicleId,
            @Valid @RequestBody InviteMemberRequest request
    ) {

        InvitationResponse response =
                vehicleMembershipService.inviteMember(
                        currentUserId(),
                        vehicleId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================================================
    // LIST VEHICLE MEMBERS
    // =========================================================

    @GetMapping("/{vehicleId}/members")
    public ResponseEntity<List<VehicleMemberResponse>> getVehicleMembers(
            @PathVariable Long vehicleId
    ) {

        List<VehicleMemberResponse> members =
                vehicleMembershipService.getVehicleMembers(
                        currentUserId(),
                        vehicleId
                );

        return ResponseEntity.ok(members);
    }

    // =========================================================
    // REMOVE VEHICLE MEMBER
    // =========================================================

    @DeleteMapping("/{vehicleId}/members/{memberUserId}")
    public ResponseEntity<Void> removeVehicleMember(
            @PathVariable Long vehicleId,
            @PathVariable Long memberUserId
    ) {

        vehicleMembershipService.removeVehicleMember(
                currentUserId(),
                vehicleId,
                memberUserId
        );

        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // CURRENT USER'S PENDING INVITATIONS
    // =========================================================

    @GetMapping("/invitations/my")
    public ResponseEntity<List<InvitationResponse>>
    getMyPendingInvitations() {

        List<InvitationResponse> invitations =
                vehicleMembershipService
                        .getPendingInvitationsForUser(
                                currentUserId()
                        );

        return ResponseEntity.ok(invitations);
    }

    // =========================================================
    // ACCEPT INVITATION
    // =========================================================

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<VehicleResponse> acceptInvitation(
            @PathVariable Long invitationId
    ) {

        VehicleResponse response =
                vehicleMembershipService.acceptInvitation(
                        currentUserId(),
                        invitationId
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DECLINE INVITATION
    // =========================================================

    @PostMapping("/invitations/{invitationId}/decline")
    public ResponseEntity<InvitationResponse> declineInvitation(
            @PathVariable Long invitationId
    ) {

        InvitationResponse response =
                vehicleMembershipService.declineInvitation(
                        currentUserId(),
                        invitationId
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // CURRENT AUTHENTICATED USER
    // =========================================================

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}