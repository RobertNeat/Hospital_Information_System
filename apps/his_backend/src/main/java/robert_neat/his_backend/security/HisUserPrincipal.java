package robert_neat.his_backend.security;

import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

import robert_neat.his_backend.common.security.StaffPrincipal;
import robert_neat.his_backend.staff.StaffRole;

/** Principal zbudowany z claimow tokenu JWT (bez dostepu do bazy). */
public record HisUserPrincipal(UUID accountId, UUID staffId, String employeeId, StaffRole role, UUID wardId)
        implements StaffPrincipal, AuthenticatedPrincipal {

    @Override
    public String getName() {
        return employeeId;
    }
}
