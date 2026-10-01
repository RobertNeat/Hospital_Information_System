package robert_neat.his_backend.alert;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertAcknowledgementRepository extends JpaRepository<AlertAcknowledgement, AlertAcknowledgementId> {

    /** Potwierdzenia wskazanych alertow zlozone przez jednego pracownika. */
    @Query("select a from AlertAcknowledgement a where a.id.staffId = :staffId and a.id.alertId in :alertIds")
    List<AlertAcknowledgement> findByStaffAndAlerts(@Param("staffId") UUID staffId,
            @Param("alertIds") Collection<UUID> alertIds);

    @Query("select a from AlertAcknowledgement a where a.id.staffId = :staffId and a.id.alertId = :alertId")
    Optional<AlertAcknowledgement> findByStaffAndAlert(@Param("staffId") UUID staffId,
            @Param("alertId") UUID alertId);

    /**
     * Idempotentny zapis potwierdzenia: pierwsze wygrywa (kto/kiedy), kolejne - rowniez wspolbiezne - nic nie zmieniaja
     * (`ON CONFLICT DO NOTHING`, bez wyjatku naruszenia PK). Zwraca 1, gdy wstawiono nowy wiersz.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into alert_acknowledgement (alert_id, staff_id, acknowledged_at)
            values (:alertId, :staffId, :at)
            on conflict (alert_id, staff_id) do nothing
            """, nativeQuery = true)
    int acknowledge(@Param("alertId") UUID alertId, @Param("staffId") UUID staffId, @Param("at") Instant at);
}
