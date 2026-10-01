package robert_neat.his_backend.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import robert_neat.his_backend.common.wire.WireEnums;
import robert_neat.his_backend.staff.StaffRole;

/**
 * Zamienia zweryfikowany JWT na uwierzytelnienie z principalem {@link HisUserPrincipal}
 * (dzieki temu dziala {@code CurrentActor}/audyt). Uprawnienia pochodza z claima `authorities`.
 */
public class HisJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            StaffRole role = WireEnums.fromWire(StaffRole.class, jwt.getClaimAsString(TokenClaims.ROLE));
            HisUserPrincipal principal = new HisUserPrincipal(
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getClaimAsString(TokenClaims.STAFF_ID)),
                    jwt.getClaimAsString(TokenClaims.EMPLOYEE_ID),
                    role,
                    UUID.fromString(jwt.getClaimAsString(TokenClaims.WARD_ID)));
            List<String> claim = jwt.getClaimAsStringList(TokenClaims.AUTHORITIES);
            List<GrantedAuthority> authorities = (claim == null ? List.<String>of() : claim).stream()
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
            return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Niepoprawne claimy tokenu", e);
        }
    }
}
