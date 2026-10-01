package robert_neat.his_backend.staff;

import java.util.UUID;

/** `Ward` z kontraktu (models/staff.model.ts). */
public record WardResponse(UUID id, String name, String shortName, String floor, int beds) {
}
