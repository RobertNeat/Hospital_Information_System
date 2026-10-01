package robert_neat.his_backend.messaging;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** HandoffNoteCreateRequest z kontraktu. `fromId` jest ignorowany (aktor z tokenu). */
public record HandoffNoteCreateRequest(
        @NotNull UUID wardId,
        @NotNull LocalDate shiftDate,
        @NotNull ShiftType shift,
        UUID fromId,
        @NotNull UUID toId,
        String generalNotes,
        @NotNull List<@NotNull @Valid HandoffPatientNoteDto> patientNotes) {
}
