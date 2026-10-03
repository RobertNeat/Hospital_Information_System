package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ClinicalNoteCreateRequest z kontraktu. patientId (jesli podany) musi byc zgodny ze sciezka; autorem jest zawsze
 * zalogowany pracownik (sesja), wiec pole authorId nie wystepuje w zadaniu.
 */
public record ClinicalNoteCreateRequest(
        UUID patientId,
        UUID encounterId,
        @NotNull NoteCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @Size(max = 50) List<@NotBlank @Size(max = 200) String> symptoms) {
}
