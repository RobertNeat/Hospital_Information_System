package robert_neat.his_backend.lab;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import robert_neat.his_backend.catalog.SpecimenType;

/** `LabOrderItem` w zadaniu utworzenia; `id`, `testName` (snapshot z katalogu) i `specimenId` sa ignorowane. */
public record LabOrderItemRequest(
        UUID id,
        @NotBlank String testCode,
        String testName,
        UUID specimenId,
        @NotNull SpecimenType specimenType) {
}
