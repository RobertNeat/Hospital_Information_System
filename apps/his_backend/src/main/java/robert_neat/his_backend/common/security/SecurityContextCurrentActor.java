package robert_neat.his_backend.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Aktor z `SecurityContext`: principal implementujacy {@link StaffPrincipal}, inaczej pusty. */
@Component
class SecurityContextCurrentActor implements CurrentActor {

    @Override
    public Optional<UUID> staffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof StaffPrincipal principal) {
            return Optional.ofNullable(principal.staffId());
        }
        return Optional.empty();
    }
}
