package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    /** Notatki pacjenta od najnowszej. */
    List<ClinicalNote> findByPatientIdOrderByCreatedAtDescIdAsc(UUID patientId);

    boolean existsByIdAndPatientId(UUID id, UUID patientId);
}
