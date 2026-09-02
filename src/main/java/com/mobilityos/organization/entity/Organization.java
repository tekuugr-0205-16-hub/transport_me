package com.mobilityos.organization.entity;

import com.mobilityos.common.auditing.AuditableEntity;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "organizations")
public class Organization extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private OrganizationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    protected Organization() {
        // Required by JPA
    }

    public Organization(String name, OrganizationType type) {
        this.name = requireName(name);
        this.type = Objects.requireNonNull(
                type,
                "organization type must not be null"
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OrganizationType getType() {
        return type;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void changeType(OrganizationType type) {
        this.type = Objects.requireNonNull(
                type,
                "organization type must not be null"
        );
    }

    public void markVerified() {
        this.verificationStatus = VerificationStatus.VERIFIED;
    }

    public void markRejected() {
        this.verificationStatus = VerificationStatus.REJECTED;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "organization name must not be blank"
            );
        }

        return name.trim();
    }

    public enum OrganizationType {
        PRIVATE_OWNER,
        GOVERNMENT,
        COOPERATIVE,
        UNIVERSITY,
        COMPANY
    }

    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED
    }
}