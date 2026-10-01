package robert_neat.his_backend.alert;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * `AlertTarget` z kontraktu: typowany wskaznik na encje, ktorej dotyczy alert (bez tras UI). Kolumny
 * `target_kind` i `target_id` wystepuja razem albo wcale (CHECK w schemacie), `patientId` jest opcjonalne.
 */
@Embeddable
public record AlertTarget(
        @Column(name = "target_kind", length = 20) AlertTargetKind kind,
        @Column(name = "target_id") UUID id,
        @Column(name = "target_patient_id") UUID patientId) {
}
