package robert_neat.his_backend.ehr;

import java.util.Collection;
import java.util.List;

import robert_neat.his_backend.patient.Encounter;
import robert_neat.his_backend.patient.TreatmentEpisode;

/**
 * Reczne mapowanie encja -> DTO (bez MapStruct). Kolekcje leniwe (objawy, ATC, diagnozy epizodu) czytane sa tu, wiec
 * mapowanie musi sie odbywac w transakcji serwisu (open-in-view wylaczone). Zbiory sortowane dla stabilnego JSON.
 */
final class EhrMapper {

    private EhrMapper() {
    }

    static ClinicalNoteResponse toResponse(ClinicalNote n) {
        List<String> symptoms = sortedOrNull(n.getSymptoms());
        return new ClinicalNoteResponse(n.getId(), n.getPatientId(), n.getEncounterId(), n.getAuthorId(),
                n.getCategory(), n.getTitle(), n.getContent(), symptoms, n.getCreatedAt(), n.getCreatedById(),
                n.getUpdatedAt(), n.getUpdatedById(), n.getVersion());
    }

    static DiagnosisResponse toResponse(Diagnosis d) {
        return new DiagnosisResponse(d.getId(), d.getPatientId(), d.getEncounterId(), d.getCode(), d.getType(),
                d.getStatus(), d.getDiagnosedAt(), d.getDiagnosedById(), d.getNotes(), d.getCreatedAt(),
                d.getCreatedById(), d.getUpdatedAt(), d.getUpdatedById(), d.getVersion());
    }

    static AllergyResponse toResponse(Allergy a) {
        return new AllergyResponse(a.getId(), a.getPatientId(), a.getSubstance(), a.getCategory(), a.getReaction(),
                a.getSeverity(), a.getStatus(), a.getRecordedAt(), a.getRecordedById(), sortedOrNull(a.getAtcCodes()),
                a.getCreatedAt(), a.getCreatedById(), a.getUpdatedAt(), a.getUpdatedById(), a.getVersion());
    }

    static ContraindicationResponse toResponse(Contraindication c) {
        return new ContraindicationResponse(c.getId(), c.getPatientId(), c.getDescription(), c.getReason(),
                c.getRecordedAt());
    }

    static TreatmentResponse toResponse(Treatment t) {
        return new TreatmentResponse(t.getId(), t.getPatientId(), t.getEncounterId(), t.getName(), t.getType(),
                t.getStartAt(), t.getEndAt(), t.getStatus(), t.getDescription(), t.getPractitionerId());
    }

    static EncounterResponse toResponse(Encounter e) {
        return new EncounterResponse(e.getId(), e.getPatientId(), e.getType(), e.getStatus(), e.getStartAt(),
                e.getEndAt(), e.getWardId(), e.getPractitionerId(), e.getReason(), e.getSummary(), e.getEpisodeId());
    }

    static TreatmentEpisodeResponse toResponse(TreatmentEpisode e) {
        return new TreatmentEpisodeResponse(e.getId(), e.getPatientId(), e.getTitle(), e.getStartAt(), e.getEndAt(),
                e.getStatus(), e.getDiagnosisIds().stream().sorted().toList());
    }

    /** Posortowana lista albo null (pole opcjonalne pomijane w JSON), gdy kolekcja jest pusta. */
    private static <T extends Comparable<? super T>> List<T> sortedOrNull(Collection<T> values) {
        return values.isEmpty() ? null : values.stream().sorted().toList();
    }
}
