package robert_neat.his_backend.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** `IdentityDocument` z kontraktu (`identity_doc_type`, `identity_doc_number`); obie kolumny razem albo wcale. */
@Embeddable
public record IdentityDocument(
        @NotNull @Column(name = "identity_doc_type", length = 20) IdentityDocumentType type,
        @NotBlank @Size(max = 50) @Column(name = "identity_doc_number", length = 50) String number) {
}
