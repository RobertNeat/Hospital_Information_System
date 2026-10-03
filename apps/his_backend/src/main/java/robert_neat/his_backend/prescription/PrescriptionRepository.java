package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Zapis klucza z e-receipt zbiorczym JPQL (omija `@Version`): wersja zwrocona klientowi przy wystawieniu
     * pozostaje aktualna, wiec kolejne `cancel` z `version` nie dostaje 409.
     */
    @Modifying
    @Query("update Prescription p set p.eRxKey = :key where p.id = :id")
    int updateERxKey(@Param("id") UUID id, @Param("key") String key);

    /**
     * Jak {@link #updateERxKey}, ale tylko gdy zapisany klucz wciaz jest tym odczytanym przed wyslaniem `POST`
     * (`:staleKey`) - rozstrzyga wyscig przy ponowieniu (retry outboxa): jesli inny watek (np. wczesniejsza proba)
     * zdazyl juz zapisac nowszy klucz, retry nie nadpisuje go (0 zaktualizowanych wierszy).
     */
    @Modifying
    @Query("update Prescription p set p.eRxKey = :key where p.id = :id and p.eRxKey = :staleKey")
    int updateERxKeyIfStillLocal(@Param("id") UUID id, @Param("staleKey") String staleKey, @Param("key") String key);
}
