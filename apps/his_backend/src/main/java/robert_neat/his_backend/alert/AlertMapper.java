package robert_neat.his_backend.alert;

/** Reczne mapowanie encja -> DTO (`acknowledgement` = potwierdzenie zalogowanego uzytkownika albo `null`). */
final class AlertMapper {

    private AlertMapper() {
    }

    static AlertResponse toResponse(ClinicalAlert a, AlertAcknowledgement acknowledgement) {
        AlertTarget t = a.getTarget();
        AlertTargetDto target = t == null || t.kind() == null ? null
                : new AlertTargetDto(t.kind(), t.id(), t.patientId());
        return new AlertResponse(a.getId(), a.getType(), a.getSeverity(), a.getPatientId(), a.getMessage(),
                a.getCreatedAt(), acknowledgement != null, acknowledgement == null ? null : acknowledgement.staffId(),
                acknowledgement == null ? null : acknowledgement.getAcknowledgedAt(), target);
    }
}
