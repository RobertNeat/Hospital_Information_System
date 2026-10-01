package robert_neat.his_backend.common.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Audyt wg schematu (`created_at`, `created_by_id`, `updated_at`, `updated_by_id`; FK do `staff_member`).
 * Wypelniany przez Spring Data JPA Auditing; aktor z {@code CurrentActor} (pusty = kolumna NULL).
 * Nie dotyczy tabel bez tych kolumn (np. `ward`, `staff_member`, `user_account`).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by_id")
    private UUID updatedById;
}
