package robert_neat.his_backend.prescription;

import java.math.BigDecimal;
import java.util.List;

import robert_neat.his_backend.catalog.AdministrationRoute;

/** `DosageInstruction` z kontraktu (pola opcjonalne - `timesOfDay`, `maxPerDay`, `instructions` - pomijane, gdy brak). */
public record DosageResponse(
        BigDecimal dose,
        String doseUnit,
        AdministrationRoute route,
        DoseFrequency frequency,
        List<TimeOfDay> timesOfDay,
        int durationDays,
        boolean asNeeded,
        Integer maxPerDay,
        String instructions) {
}
