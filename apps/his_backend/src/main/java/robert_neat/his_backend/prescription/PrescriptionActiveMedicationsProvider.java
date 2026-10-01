package robert_neat.his_backend.prescription;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.ehr.ActiveMedicationsProvider;

/**
 * Dostawca `ehr-summary.activeMedications` z recept (zastepuje domyslny pusty dostawca z modulu ehr). Te same
 * dane co `GET /patients/{id}/active-medications`.
 */
@Component
class PrescriptionActiveMedicationsProvider implements ActiveMedicationsProvider {

    private final PrescriptionService prescriptions;

    PrescriptionActiveMedicationsProvider(PrescriptionService prescriptions) {
        this.prescriptions = prescriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveMedicationResponse> activeMedications(UUID patientId) {
        return prescriptions.activeMedications(patientId);
    }
}
