package robert_neat.his_backend.ehr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ClinicalNote z kontraktu. Pola opcjonalne (null) sa pomijane (NON_ABSENT); symptoms pomijane gdy brak. */
public record ClinicalNoteResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        UUID authorId,
        NoteCategory category,
        String title,
        String content,
        List<String> symptoms,
        Instant createdAt,
        UUID createdById,
        Instant updatedAt,
        UUID updatedById,
        long version) {
}
