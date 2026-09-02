package com.mobilityos.organization.controller;

import com.mobilityos.organization.dto.CreateOrganizationRequest;
import com.mobilityos.organization.dto.OrganizationResponse;
import com.mobilityos.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(
            OrganizationService organizationService
    ) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request
    ) {

        Long userId = currentUserId();

        OrganizationResponse response =
                organizationService.createOrganization(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrganizationResponse>> getMyOrganizations() {

        Long userId = currentUserId();

        return ResponseEntity.ok(
                organizationService.getOrganizationsForUser(userId)
        );
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}