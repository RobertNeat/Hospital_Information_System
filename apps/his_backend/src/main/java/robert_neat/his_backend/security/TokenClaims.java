package robert_neat.his_backend.security;

/** Nazwy claimow tokenu (poza standardowymi `sub`, `iss`, `iat`, `exp`). */
public final class TokenClaims {

    public static final String STAFF_ID = "staffId";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String ROLE = "role";
    public static final String WARD_ID = "wardId";
    public static final String AUTHORITIES = "authorities";

    private TokenClaims() {
    }
}
