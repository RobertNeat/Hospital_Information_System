package robert_neat.his_backend.patient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

    Optional<Admission> findByPatientIdAndStatus(UUID patientId, AdmissionRecordStatus status);

    boolean existsByPatientIdAndStatus(UUID patientId, AdmissionRecordStatus status);

    List<Admission> findByPatientIdInAndStatus(Collection<UUID> patientIds, AdmissionRecordStatus status);

    /** Przyjecia w danym statusie (przeglad parametrow zyciowych: aktywne przyjecia wszystkich oddzialow). */
    List<Admission> findByStatus(AdmissionRecordStatus status);

    List<Admission> findByWardIdAndStatus(UUID wardId, AdmissionRecordStatus status);

    /** Historia pacjenta od najnowszego, opcjonalnie tylko o danym statusie. */
    @Query("""
            select a from Admission a
            where a.patientId = :patientId
              and (:status is null or a.status = :status)
            order by a.admittedAt desc, a.id
            """)
    List<Admission> history(@Param("patientId") UUID patientId, @Param("status") AdmissionRecordStatus status);
}
