package robert_neat.his_backend.lab;

/**
 * `ResultAcknowledgeRequest` z kontraktu (opcjonalne cialo). `version` jest akceptowane dla zgodnosci, ale ignorowane:
 * `lab_result` nie ma kolumny `version`, a potwierdzenie jest idempotentne.
 */
public record ResultAcknowledgeRequest(Long version) {
}
