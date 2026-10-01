package robert_neat.his_backend.patient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** `PatientSummary` z kontraktu; `wardName`/`bed` tylko dla pacjentow `admitted` (projekcja aktywnego przyjecia). */
public record PatientSummaryResponse(
        UUID id,
        String mrn,
        @JsonInclude(JsonInclude.Include.ALWAYS) String pesel,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Gender gender,
        PatientStatus status,
        List<PatientFlag> flags,
        String wardName,
        String bed) {
}
