package robert_neat.his_backend.auth;

import java.util.UUID;

import robert_neat.his_backend.staff.StaffAccountStatus;

/** `StaffRegistrationResponse` z kontraktu (`accountStatus` zawsze `pending`). */
public record StaffRegistrationResponse(UUID staffId, StaffAccountStatus accountStatus) {
}
