package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ClinicalNoteCreateRequest z kontraktu. patientId (jesli podany) musi byc zgodny ze sciezka, a authorId jest
 * ignorowany - autorem jest zalogowany pracownik (sesja).
 */
public record ClinicalNoteCreateRequest(
        UUID patientId,
        UUID encounterId,
        UUID authorId,
        @NotNull NoteCategory category,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @Size(max = 50) List<@NotBlank @Size(max = 200) String> symptoms) {
}
