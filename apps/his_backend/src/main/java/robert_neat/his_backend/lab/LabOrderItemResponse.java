package robert_neat.his_backend.lab;

import java.util.UUID;

import robert_neat.his_backend.catalog.SpecimenType;

/** `LabOrderItem` z kontraktu; `testName` i `specimenType` to zapisany snapshot (nie biezacy katalog). */
public record LabOrderItemResponse(UUID id, String testCode, String testName, UUID specimenId,
        SpecimenType specimenType) {
}
