package robert_neat.his_backend.ehr;

import java.util.List;

/** EhrSummary z kontraktu: projekcja liczona przez backend (nie zapisywana przez klienta). */
public record EhrSummaryResponse(
        List<DiagnosisResponse> recentDiagnoses,
        List<DiagnosisResponse> chronicConditions,
        List<? extends PrescriptionItemView> activeMedications,
        List<EncounterResponse> recentEncounters,
        List<AllergyResponse> allergies) {
}
