package robert_neat.his_backend.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * Audyt + optymistyczne blokowanie (`version bigint NOT NULL DEFAULT 0`).
 * Konflikt wersji -> {@code ObjectOptimisticLockingFailureException} -> 409 CONFLICT.
 */
@MappedSuperclass
@Getter
public abstract class VersionedEntity extends AuditableEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
