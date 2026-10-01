package robert_neat.his_backend.vitals;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VitalSignsRepository extends JpaRepository<VitalSigns, UUID> {

    /** Odczyty pacjenta rosnaco po `recordedAt` (remisy: id). */
    List<VitalSigns> findByPatientIdOrderByRecordedAtAscIdAsc(UUID patientId);

    /** Odczyty pacjenta od `from` (wlacznie), rosnaco po `recordedAt`. */
    List<VitalSigns> findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAscIdAsc(UUID patientId,
            Instant from);

    Optional<VitalSigns> findFirstByPatientIdOrderByRecordedAtDescIdAsc(UUID patientId);

    /** Najnowsze odczyty podanych pacjentow (przy remisie moze byc wiecej niz jeden na pacjenta - wybiera serwis). */
    @Query("""
            select v from VitalSigns v
            where v.patientId in :patientIds
              and v.recordedAt = (select max(v2.recordedAt) from VitalSigns v2 where v2.patientId = v.patientId)
            """)
    List<VitalSigns> latestFor(@Param("patientIds") Collection<UUID> patientIds);
}
