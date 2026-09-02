package com.mobilityos.identity.entity;

import com.mobilityos.common.auditing.AuditableEntity;
import jakarta.persistence.*;

/**
 * The single permanent identity for a person interacting with MobilityOS.
 *
 * A User is only an authenticated person. Vehicle access is not represented
 * by global OWNER/DRIVER roles; it is granted through VehicleMember records.
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    protected User() {
        // JPA
    }

    public User(String phoneNumber, String passwordHash) {
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public enum UserStatus {
        ACTIVE, SUSPENDED, PENDING_VERIFICATION
    }
}
