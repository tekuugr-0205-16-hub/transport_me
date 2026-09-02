package com.mobilityos.organization.membership;

import com.mobilityos.common.auditing.AuditableEntity;
import com.mobilityos.identity.entity.User;
import com.mobilityos.organization.entity.Organization;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(
        name = "organization_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_member",
                        columnNames = {"organization_id", "user_id"}
                )
        }
)
public class OrganizationMember extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 30)
    private OrganizationMemberRole role;

    protected OrganizationMember() {
        // Required by JPA
    }

    public OrganizationMember(
            Organization organization,
            User user,
            OrganizationMemberRole role
    ) {
        this.organization = Objects.requireNonNull(
                organization,
                "organization must not be null"
        );

        this.user = Objects.requireNonNull(
                user,
                "user must not be null"
        );

        this.role = Objects.requireNonNull(
                role,
                "role must not be null"
        );
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public OrganizationMemberRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return role == OrganizationMemberRole.OWNER;
    }

    public boolean isAdmin() {
        return role == OrganizationMemberRole.ADMIN;
    }

    public boolean canManageFleet() {
        return isOwner() || isAdmin();
    }

    public boolean canManageAdmins() {
        return isOwner();
    }

    public void changeRole(OrganizationMemberRole newRole) {
        this.role = Objects.requireNonNull(
                newRole,
                "role must not be null"
        );
    }
}