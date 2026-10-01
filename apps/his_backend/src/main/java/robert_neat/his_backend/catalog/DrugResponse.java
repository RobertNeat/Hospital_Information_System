package robert_neat.his_backend.catalog;

import java.util.List;
import java.util.UUID;

/** `Drug` z kontraktu (`interactsWithAtc` i `maxDailyDose` opcjonalne - pomijane, gdy brak). */
public record DrugResponse(
        UUID id,
        String name,
        String activeSubstance,
        String atcCode,
        DrugForm form,
        String strength,
        int packageSize,
        String packageUnit,
        List<AdministrationRoute> routes,
        String defaultDoseUnit,
        boolean rxOnly,
        List<ReimbursementLevel> reimbursementOptions,
        List<String> interactsWithAtc,
        DoseQuantityResponse maxDailyDose) {
}
