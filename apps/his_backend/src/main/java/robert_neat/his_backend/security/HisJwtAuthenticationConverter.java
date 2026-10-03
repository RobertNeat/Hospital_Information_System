package robert_neat.his_backend.security;

import java.util.List;
import java.util.Optional;
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
 * <p>
 * Dodatkowo (jesli podano {@link TokenVersionLookup}) porownuje claim `tv` z biezaca wersja konta w
 * bazie - niezgodnosc (konto zablokowane/rola zmieniona/wylogowanie-wszedzie od czasu wydania tokenu)
 * konczy sie 401, tak samo jak zly podpis czy wygasniecie. Lookup jest celowo POZA blokiem
 * {@code catch (RuntimeException)} ponizej: wyjatek infrastrukturalny (np. chwilowa niedostepnosc
 * bazy) nie powinien byc mylony z niepoprawnymi claimami i nie powinien wylogowywac wszystkich.
 */
public class HisJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TokenVersionLookup tokenVersions;

    public HisJwtAuthenticationConverter() {
        this(null);
    }

    public HisJwtAuthenticationConverter(TokenVersionLookup tokenVersions) {
        this.tokenVersions = tokenVersions;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        HisUserPrincipal principal;
        List<GrantedAuthority> authorities;
        try {
            StaffRole role = WireEnums.fromWire(StaffRole.class, jwt.getClaimAsString(TokenClaims.ROLE));
            principal = new HisUserPrincipal(
                    UUID.fromString(jwt.getSubject()),
                    UUID.fromString(jwt.getClaimAsString(TokenClaims.STAFF_ID)),
                    jwt.getClaimAsString(TokenClaims.EMPLOYEE_ID),
                    role,
                    UUID.fromString(jwt.getClaimAsString(TokenClaims.WARD_ID)));
            List<String> claim = jwt.getClaimAsStringList(TokenClaims.AUTHORITIES);
            authorities = (claim == null ? List.<String>of() : claim).stream()
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("Niepoprawne claimy tokenu", e);
        }
        if (tokenVersions != null && !tokenVersionMatches(jwt, principal.accountId())) {
            throw new InvalidBearerTokenException("Token uniewazniony (zmiana stanu konta)");
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }

    /**
     * Fix: Nimbus/Jackson deserializuje liczby calkowite z JWT jako `Long`, nawet gdy zostaly zapisane
     * jako `int` (`JwtEncoder` nie zachowuje typu); rzutowanie wprost na `Integer` konczylo sie
     * `ClassCastException` zamiast odrzucenia tokenu. Stad porownanie przez {@link Number#intValue()}.
     */
    private boolean tokenVersionMatches(Jwt jwt, UUID accountId) {
        Number claimed = jwt.getClaim(TokenClaims.TOKEN_VERSION);
        Optional<Integer> current = tokenVersions.currentVersion(accountId);
        return claimed != null && current.isPresent() && claimed.intValue() == current.get();
    }
}
