package robert_neat.his_backend.alert;

import java.time.Instant;
import java.util.UUID;

/**
 * `ClinicalAlert` z kontraktu. `acknowledged`/`acknowledgedById`/`acknowledgedAt` (`@viewerScoped`) dotycza
 * zalogowanego uzytkownika. Pole `link` (`@deprecated`) nie jest zwracane - backend nie zna tras UI.
 */
public record AlertResponse(
        UUID id,
        AlertType type,
        AlertSeverity severity,
        UUID patientId,
        String message,
        Instant createdAt,
        boolean acknowledged,
        UUID acknowledgedById,
        Instant acknowledgedAt,
        AlertTargetDto target) {
}
