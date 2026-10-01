package robert_neat.his_backend.prescription;

import java.util.UUID;

import robert_neat.his_backend.catalog.DrugForm;
import robert_neat.his_backend.catalog.ReimbursementLevel;

/** `PrescriptionItem` z kontraktu; `drugName`/`activeSubstance`/`strength`/`form` to snapshot z chwili wystawienia. */
public record PrescriptionItemResponse(
        UUID id,
        UUID drugId,
        String drugName,
        String activeSubstance,
        String strength,
        DrugForm form,
        DosageResponse dosage,
        int quantityPackages,
        ReimbursementLevel reimbursement,
        boolean substitutionAllowed) {
}
