package robert_neat.his_backend.lab;

/**
 * `ResultAcknowledgeRequest` z kontraktu (opcjonalne cialo). `version`, gdy podana, musi zgadzac sie z biezaca wersja
 * wyniku (409 `CONFLICT` w przeciwnym razie); `null` pomija sprawdzenie.
 */
public record ResultAcknowledgeRequest(Long version) {
}
