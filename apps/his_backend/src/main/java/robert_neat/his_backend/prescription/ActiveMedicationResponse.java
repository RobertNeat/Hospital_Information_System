package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.UUID;

import robert_neat.his_backend.catalog.DrugForm;
import robert_neat.his_backend.catalog.ReimbursementLevel;
import robert_neat.his_backend.ehr.PrescriptionItemView;

/**
 * `ActiveMedication` z kontraktu: pozycja zywej recepty z `prescriptionId` i `date` (= `validFrom` recepty).
 * Implementuje znacznik {@link PrescriptionItemView}, wiec trafia tez do `ehr-summary.activeMedications`.
 */
public record ActiveMedicationResponse(
        UUID id,
        UUID drugId,
        String drugName,
        String activeSubstance,
        String strength,
        DrugForm form,
        DosageResponse dosage,
        int quantityPackages,
        ReimbursementLevel reimbursement,
        boolean substitutionAllowed,
        UUID prescriptionId,
        LocalDate date) implements PrescriptionItemView {
}
