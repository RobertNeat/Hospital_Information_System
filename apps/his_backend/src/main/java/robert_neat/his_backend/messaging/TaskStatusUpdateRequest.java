package robert_neat.his_backend.messaging;

import jakarta.validation.constraints.NotNull;

/** TaskStatusUpdateRequest z kontraktu; `version` (opcjonalny) - kontrola wspolbieznosci (409). */
public record TaskStatusUpdateRequest(@NotNull TaskStatus status, Long version) {
}
