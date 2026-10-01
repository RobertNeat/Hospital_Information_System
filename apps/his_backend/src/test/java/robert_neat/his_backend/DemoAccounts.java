package robert_neat.his_backend;

import java.util.List;
import java.util.Map;

/**
 * Konta proste z changelogu `db/changelog/demo/001-demo-accounts.sql` (kontekst `reference`, takze produkcja):
 * login = identyfikator = haslo. Wiersze licza sie do `ward`, `staff_member` i `user_account` w KAZDYM kontekscie.
 */
public final class DemoAccounts {

    public static final String WARD_ID = "d8e88b10-bf2b-3513-9d82-1f4d60114c5f";
    public static final int WARDS = 1;

    /** login (= haslo) -> rola na drucie. */
    public static final Map<String, String> ROLE_BY_LOGIN = Map.of(
            "admin", "admin",
            "user", "doctor",
            "doctor", "doctor",
            "nurse", "nurse",
            "lab-tech", "lab_technician",
            "radiologist", "radiologist",
            "pharmacist", "pharmacist",
            "registrar", "registrar");

    public static final int STAFF = ROLE_BY_LOGIN.size();
    public static final int ACCOUNTS = ROLE_BY_LOGIN.size();

    /** Dodatkowe wiersze demo w tabelach wspoldzielonych z danymi mock. */
    public static final Map<String, Integer> EXTRA_ROWS = Map.of(
            "ward", WARDS,
            "staff_member", STAFF,
            "user_account", ACCOUNTS);

    public static final List<String> LOGINS = List.copyOf(ROLE_BY_LOGIN.keySet());

    private DemoAccounts() {
    }
}
