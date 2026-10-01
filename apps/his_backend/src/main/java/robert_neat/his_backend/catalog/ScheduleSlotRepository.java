package robert_neat.his_backend.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, UUID> {

    /** Sloty modalnosci o poczatku w polotwartym przedziale [from, to), wg poczatku i sali. */
    List<ScheduleSlot> findByModalityAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAscRoomAscIdAsc(
            ImagingModality modality, Instant from, Instant to);

    /** Slot z blokada zapisu (serializuje konkurencyjne rezerwacje i zwolnienia tego samego slotu). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleSlot s where s.id = :id")
    Optional<ScheduleSlot> findByIdForUpdate(@Param("id") UUID id);
}
