package robert_neat.his_backend.realtime;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import robert_neat.his_backend.security.HisUserPrincipal;

/**
 * Uwierzytelnienie sesji STOMP. `getName()` = `staffId` (nie `employeeId` jak w {@link HisUserPrincipal}):
 * to nazwa uzytkownika, po ktorej broker kieruje `/user/queue/**`, a push adresuje `convertAndSendToUser(staffId)`.
 */
public final class StompUserAuthentication extends AbstractAuthenticationToken {

    private final HisUserPrincipal principal;

    StompUserAuthentication(HisUserPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    public UUID staffId() {
        return principal.staffId();
    }

    @Override
    public HisUserPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return principal.staffId().toString();
    }
}
