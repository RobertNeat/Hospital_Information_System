package robert_neat.his_backend.security;

/** Nazwy claimow tokenu (poza standardowymi `sub`, `iss`, `iat`, `exp`). */
public final class TokenClaims {

    public static final String STAFF_ID = "staffId";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String ROLE = "role";
    public static final String WARD_ID = "wardId";
    public static final String AUTHORITIES = "authorities";
    /** Stempel wersji tokenu konta w chwili wystawienia (patrz {@code UserAccount.tokenVersion}). */
    public static final String TOKEN_VERSION = "tv";

    private TokenClaims() {
    }
}
