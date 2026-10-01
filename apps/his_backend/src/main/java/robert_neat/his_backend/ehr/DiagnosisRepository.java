package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, UUID> {

    List<Diagnosis> findByPatientIdOrderByDiagnosedAtDescIdAsc(UUID patientId);

    /** 5 najnowszych diagnoz (EhrSummary.recentDiagnoses). */
    List<Diagnosis> findTop5ByPatientIdOrderByDiagnosedAtDescIdAsc(UUID patientId);

    /** Schorzenia przewlekle (EhrSummary.chronicConditions). */
    List<Diagnosis> findByPatientIdAndTypeOrderByDiagnosedAtDescIdAsc(UUID patientId, DiagnosisType type);

    Optional<Diagnosis> findByIdAndPatientId(UUID id, UUID patientId);
}
