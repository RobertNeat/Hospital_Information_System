package robert_neat.his_backend.prescription;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Generator kodow recepty (kryptograficznie losowy {@link SecureRandom}, bez zewnetrznych zaleznosci).
 * <ul>
 *   <li>`accessCode` - 4 cyfry `0000`-`9999` (kolumna `char(4)`, CHECK `^[0-9]{4}$`); kod dostepu nie jest unikalny.</li>
 *   <li>`eRxKey` - LOKALNY klucz 44 znakow `A-Z0-9` (kolumna `char(44)`; ten sam alfabet co dane mock i UI).
 *       To NIE jest klucz wydany przez system e-recepty: dla `e_prescription` podmienia go po commicie
 *       `EReceiptIntegration` kluczem z e-receipt (gdy integracja wlaczona i e-receipt odpowiada).</li>
 * </ul>
 */
@Component
public class PrescriptionCodeGenerator {

    public static final int ACCESS_CODE_LENGTH = 4;
    public static final int ERX_KEY_LENGTH = 44;

    private static final String ERX_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SecureRandom random = new SecureRandom();

    public String accessCode() {
        return String.format("%0" + ACCESS_CODE_LENGTH + "d", random.nextInt(10_000));
    }

    public String eRxKey() {
        StringBuilder key = new StringBuilder(ERX_KEY_LENGTH);
        for (int i = 0; i < ERX_KEY_LENGTH; i++) {
            key.append(ERX_ALPHABET.charAt(random.nextInt(ERX_ALPHABET.length())));
        }
        return key.toString();
    }
}
