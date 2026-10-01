package robert_neat.his_backend.prescription;

/** `PrescriptionCancelRequest` z kontraktu; `reason` opcjonalny, `version` (opcjonalna) - kontrola wspolbieznosci (409). */
public record PrescriptionCancelRequest(String reason, Long version) {
}
