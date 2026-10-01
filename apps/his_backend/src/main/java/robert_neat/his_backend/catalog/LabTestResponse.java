package robert_neat.his_backend.catalog;

import java.util.List;

/** `LabTest` z kontraktu (`loinc` opcjonalny; `analytes` zawsze obecne, byc moze puste). */
public record LabTestResponse(
        String code,
        String loinc,
        String name,
        LabCategory category,
        List<SpecimenType> specimenTypes,
        SpecimenType defaultSpecimen,
        int turnaroundHours,
        boolean fastingRequired,
        List<LabAnalyteDefinitionResponse> analytes) {
}
