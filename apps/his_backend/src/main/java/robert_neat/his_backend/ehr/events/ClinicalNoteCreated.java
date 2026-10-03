package robert_neat.his_backend.ehr.events;

import java.time.Instant;
import java.util.UUID;

import robert_neat.his_backend.ehr.NoteCategory;

/** Zdarzenie domenowe: dodano notatke kliniczna (publikowane w transakcji; konsument: `alert/AlertEventListener`, alert `system`). */
public record ClinicalNoteCreated(
        UUID noteId,
        UUID patientId,
        UUID encounterId,
        UUID authorId,
        NoteCategory category,
        Instant createdAt) {
}
