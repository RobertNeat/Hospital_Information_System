package robert_neat.his_backend.catalog;

import java.util.List;
import java.util.UUID;

/** `LabPanel` z kontraktu. */
public record LabPanelResponse(UUID id, String name, List<String> testCodes) {
}
