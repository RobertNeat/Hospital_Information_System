package robert_neat.his_backend.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import robert_neat.his_backend.staff.StaffRole;

/** Wystawia podpisane tokeny dostepu (HS256); stan sesji nie jest przechowywany po stronie serwera. */
public class JwtTokenService {

    /** Token wraz z momentem wygasniecia (sekundowa precyzja, zgodna z claimem `exp`). */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(UUID accountId, UUID staffId, String employeeId, StaffRole role, UUID wardId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(accountId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(TokenClaims.STAFF_ID, staffId.toString())
                .claim(TokenClaims.EMPLOYEE_ID, employeeId)
                .claim(TokenClaims.ROLE, role.wire())
                .claim(TokenClaims.WARD_ID, wardId.toString())
                .claim(TokenClaims.AUTHORITIES, RolePermissions.authoritiesOf(role))
                .build();
        String token = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
