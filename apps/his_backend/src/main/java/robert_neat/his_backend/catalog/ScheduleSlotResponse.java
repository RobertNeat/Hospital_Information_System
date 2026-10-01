package robert_neat.his_backend.catalog;

import java.time.Instant;
import java.util.UUID;

/** `ScheduleSlot` z kontraktu (`start`/`end` to kolumny `start_at`/`end_at`, UTC). */
public record ScheduleSlotResponse(
        UUID id,
        ImagingModality modality,
        Instant start,
        Instant end,
        String room,
        boolean available) {
}
