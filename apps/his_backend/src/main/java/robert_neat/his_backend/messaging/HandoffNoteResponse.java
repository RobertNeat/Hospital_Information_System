package robert_neat.his_backend.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HandoffNote z kontraktu; `patientNotes` rosnaco po `patientId`. */
public record HandoffNoteResponse(
        UUID id,
        UUID wardId,
        LocalDate shiftDate,
        ShiftType shift,
        UUID fromId,
        UUID toId,
        Instant createdAt,
        String generalNotes,
        List<HandoffPatientNoteDto> patientNotes) {
}
