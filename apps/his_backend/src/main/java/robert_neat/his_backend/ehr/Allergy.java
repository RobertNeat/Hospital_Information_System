package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import robert_neat.his_backend.common.persistence.VersionedEntity;

/** Alergia (`allergy`) z kodami ATC (`allergy_atc_code`) uzywanymi przez kontrole bezpieczenstwa leku. */
@Entity
@Table(name = "allergy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Allergy extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "substance", nullable = false, length = 200)
    private String substance;

    @Column(name = "category", nullable = false, length = 15)
    private AllergyCategory category;

    @Column(name = "reaction", nullable = false, length = 500)
    private String reaction;

    @Column(name = "severity", nullable = false, length = 20)
    private AllergySeverity severity;

    @Column(name = "status", nullable = false, length = 10)
    private AllergyStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by_id")
    private UUID recordedById;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "allergy_atc_code", joinColumns = @JoinColumn(name = "allergy_id"))
    @Column(name = "atc_code", nullable = false, length = 10)
    @BatchSize(size = 100)
    private Set<String> atcCodes = new HashSet<>();

    public static Allergy record(UUID patientId, String substance, AllergyCategory category, String reaction,
            AllergySeverity severity, AllergyStatus status, Instant recordedAt, UUID recordedById,
            Set<String> atcCodes) {
        Allergy a = new Allergy();
        a.patientId = patientId;
        a.substance = substance;
        a.category = category;
        a.reaction = reaction;
        a.severity = severity;
        a.status = status;
        a.recordedAt = recordedAt;
        a.recordedById = recordedById;
        a.atcCodes.addAll(atcCodes);
        return a;
    }
}
