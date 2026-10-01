package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID>,
        JpaSpecificationExecutor<Prescription> {

    /** Recepty "zywe" pacjenta (status z `statuses`, `validUntil` >= `today`), najnowsze (wg poczatku waznosci) pierwsze. */
    @Query("""
            select p from Prescription p
            where p.patientId = :patientId and p.status in :statuses and p.validUntil >= :today
            order by p.validFrom desc, p.issuedAt desc, p.id asc
            """)
    List<Prescription> findActive(@Param("patientId") UUID patientId,
            @Param("statuses") Collection<PrescriptionStatus> statuses, @Param("today") LocalDate today);
}
