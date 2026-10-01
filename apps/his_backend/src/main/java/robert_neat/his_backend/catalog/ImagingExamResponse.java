package robert_neat.his_backend.catalog;

/** `ImagingExam` z kontraktu (`preparation` opcjonalne). */
public record ImagingExamResponse(
        String code,
        ImagingModality modality,
        String name,
        String bodyRegion,
        boolean contrastPossible,
        boolean requiresLaterality,
        String preparation,
        int durationMinutes) {
}
