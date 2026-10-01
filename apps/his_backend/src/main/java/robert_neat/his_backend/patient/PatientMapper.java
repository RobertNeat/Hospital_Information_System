package robert_neat.his_backend.patient;

import java.util.Comparator;
import java.util.List;

/** Reczne mapowanie encja -> DTO (bez MapStruct). */
public final class PatientMapper {

    private PatientMapper() {
    }

    public static AdmissionResponse toResponse(Admission a) {
        return new AdmissionResponse(a.getId(), a.getPatientId(), a.getEncounterId(), a.getStatus(),
                a.getAdmissionType(), a.getAdmittedAt(), a.getWardId(), a.getRoom(), a.getBed(),
                a.getAttendingPhysicianId(), a.getTriageLevel(), a.getReason(), a.getReferralNumber(),
                a.getDischargedAt(), a.getDischargeDisposition(), a.getDischargeSummaryNoteId(), a.getVersion());
    }

    /** `currentAdmission` = aktywne przyjecie albo null. */
    public static PatientResponse toResponse(Patient p, Admission currentAdmission) {
        return new PatientResponse(p.getId(), p.getMrn(), p.getPesel(), p.getNoPeselReason(),
                p.getIdentityDocument(), p.getFirstName(), p.getSecondName(), p.getLastName(), p.getBirthDate(),
                p.getGender(), p.getPhone(), p.getEmail(), p.getAddress(), p.getEmergencyContact(),
                p.getInsurance(), p.getBloodType(), p.getStatus(),
                currentAdmission == null ? null : toResponse(currentAdmission), sortedFlags(p), p.getCreatedAt(),
                p.getUpdatedAt(), p.getCreatedById(), p.getUpdatedById(), p.getVersion());
    }

    public static PatientSummaryResponse toSummary(Patient p, String wardName, String bed) {
        return new PatientSummaryResponse(p.getId(), p.getMrn(), p.getPesel(), p.getFirstName(), p.getLastName(),
                p.getBirthDate(), p.getGender(), p.getStatus(), sortedFlags(p), wardName, bed);
    }

    /** Stan danych osobowych jako zadanie - baza scalania PATCH. */
    public static PatientCreateRequest toDraft(Patient p) {
        return new PatientCreateRequest(p.getPesel(), p.getNoPeselReason(), p.getIdentityDocument(),
                p.getFirstName(), p.getSecondName(), p.getLastName(), p.getBirthDate(), p.getGender(),
                p.getPhone(), p.getEmail(), p.getAddress(), p.getEmergencyContact(), p.getInsurance(),
                p.getBloodType(), sortedFlags(p));
    }

    private static List<PatientFlag> sortedFlags(Patient p) {
        return p.getFlags().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
