package robert_neat.his_backend.common.order;

import jakarta.validation.constraints.NotNull;

/** `OrderStatusUpdateRequest` z kontraktu; aktor z sesji, `version` (opcjonalny) - kontrola wspolbieznosci (409). */
public record OrderStatusUpdateRequest(@NotNull OrderStatus status, String note, Long version) {
}
