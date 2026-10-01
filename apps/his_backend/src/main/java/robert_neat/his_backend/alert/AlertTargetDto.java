package robert_neat.his_backend.alert;

import java.util.UUID;

/** `AlertTarget` z kontraktu (`patientId` pomijane, gdy brak). */
public record AlertTargetDto(AlertTargetKind kind, UUID id, UUID patientId) {
}
