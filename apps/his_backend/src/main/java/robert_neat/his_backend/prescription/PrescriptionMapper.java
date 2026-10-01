package robert_neat.his_backend.prescription;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import robert_neat.his_backend.catalog.CatalogMapper;

/** Reczne mapowanie encja -> DTO; kolekcje leniwe, wiec w transakcji serwisu (open-in-view wylaczone). */
final class PrescriptionMapper {

    private PrescriptionMapper() {
    }

    static PrescriptionResponse toResponse(Prescription p, LocalDate today) {
        return new PrescriptionResponse(p.getId(), p.getPatientId(), p.getEncounterId(), p.getPrescriberId(),
                p.getIssuedAt(), p.getValidFrom(), p.getValidUntil(), p.getKind(),
                p.getItems().stream().map(PrescriptionMapper::toResponse).toList(), p.effectiveStatus(today),
                p.getAccessCode(), p.getERxKey(), p.getNotes(), p.getCancelledAt(), p.getCancelReason(),
                p.getCreatedAt(), p.getCreatedById(), p.getUpdatedAt(), p.getUpdatedById(), p.getVersion());
    }

    static PrescriptionItemResponse toResponse(PrescriptionItem i) {
        return new PrescriptionItemResponse(i.getId(), i.getDrugId(), i.getDrugName(), i.getActiveSubstance(),
                i.getStrength(), i.getForm(), toResponse(i.getDosage(), i.getTimesOfDay()), i.getQuantityPackages(),
                i.getReimbursement(), i.isSubstitutionAllowed());
    }

    static ActiveMedicationResponse toActiveMedication(Prescription p, PrescriptionItem i) {
        return new ActiveMedicationResponse(i.getId(), i.getDrugId(), i.getDrugName(), i.getActiveSubstance(),
                i.getStrength(), i.getForm(), toResponse(i.getDosage(), i.getTimesOfDay()), i.getQuantityPackages(),
                i.getReimbursement(), i.isSubstitutionAllowed(), p.getId(), p.getValidFrom());
    }

    /** `timesOfDay` w kolejnosci doby (rano, poludnie, wieczor, noc); pusty zbior = brak pola. */
    private static DosageResponse toResponse(DosageInstruction d, Set<TimeOfDay> timesOfDay) {
        List<TimeOfDay> times = timesOfDay.isEmpty() ? null
                : timesOfDay.stream().sorted(Comparator.naturalOrder()).toList();
        return new DosageResponse(CatalogMapper.plain(d.getDose()), d.getDoseUnit(), d.getRoute(), d.getFrequency(),
                times, d.getDurationDays(), d.isAsNeeded(), d.getMaxPerDay(), d.getInstructions());
    }
}
