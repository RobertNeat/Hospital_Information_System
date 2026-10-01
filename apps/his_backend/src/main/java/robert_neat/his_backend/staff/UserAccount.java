package robert_neat.his_backend.staff;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Konto logowania pracownika (`user_account`). `passwordHash` to goly hash BCrypt (`$2a$10$...`).
 * Tabela ma tylko `created_at`/`updated_at` (bez aktora), wiec nie dziedziczy po `AuditableEntity`.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "employee_id", nullable = false, length = 30, updatable = false)
    private String employeeId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "account_status", nullable = false, length = 10)
    private StaffAccountStatus accountStatus;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Nowe konto czekajace na aktywacje przez administratora. */
    public static UserAccount pending(UUID staffId, String employeeId, String passwordHash) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.staffId = staffId;
        account.employeeId = employeeId;
        account.passwordHash = passwordHash;
        account.accountStatus = StaffAccountStatus.PENDING;
        return account;
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Nieudana proba; po przekroczeniu limitu ustawia blokade czasowa i zeruje licznik. */
    public void registerFailedAttempt(Instant now, int maxAttempts, java.time.Duration lockDuration) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void activate() {
        accountStatus = StaffAccountStatus.ACTIVE;
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void lock() {
        accountStatus = StaffAccountStatus.LOCKED;
    }
}
