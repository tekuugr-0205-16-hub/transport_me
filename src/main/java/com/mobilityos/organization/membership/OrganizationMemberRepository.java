package com.mobilityos.organization.membership;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, Long> {

    // Fast authorization check — no entity loading needed.
    boolean existsByOrganizationIdAndUserId(
            Long organizationId,
            Long userId
    );

    // Used when we need the membership role:
    // OWNER / ADMIN.
    Optional<OrganizationMember> findByOrganizationIdAndUserId(
            Long organizationId,
            Long userId
    );

    // Operator-account members screen.
    // Fetch users in the same query to avoid N+1.
    @EntityGraph(attributePaths = "user")
    List<OrganizationMember> findByOrganizationId(
            Long organizationId
    );

    // "My operator accounts".
    // Fetch organizations in the same query to avoid N+1.
    @EntityGraph(attributePaths = "organization")
    List<OrganizationMember> findByUserId(
            Long userId
    );
}