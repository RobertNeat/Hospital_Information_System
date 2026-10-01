package robert_neat.his_backend.common.order;

import jakarta.validation.constraints.NotBlank;

/** `OrderCancelRequest` z kontraktu; `reason` wymagany, `version` (opcjonalny) - kontrola wspolbieznosci (409). */
public record OrderCancelRequest(@NotBlank String reason, Long version) {
}
