package robert_neat.his_backend.ehr;

import java.util.regex.Pattern;

import robert_neat.his_backend.common.api.FieldError;

/**
 * Walidacja `Coding` przy zapisie diagnoz i wskazan klinicznych zlecen lab/obrazowych: od wprowadzenia podpowiedzi
 * terminologii SNOMED CT jedynym akceptowanym systemem jest {@link CodingSystem#SNOMED}, a `code` musi byc poprawnym
 * SCTID. Format (6-18 cyfr) jest tym samym wzorcem, co {@code SnowstormClient#lookup}/{@code #translateToIcd10} -
 * sama poprawnosc formatu, bez sprawdzania istnienia pojecia w Snowstorm (poza zakresem, zob. backend_contract).
 */
public final class CodingValidation {

    private static final Pattern SCTID = Pattern.compile("\\d{6,18}");

    private CodingValidation() {
    }

    /** Dopisuje blad do `errors`, gdy `coding` nie jest null i nie jest poprawnym SNOMED/SCTID. `null` jest dozwolone. */
    public static void requireSnomedIfPresent(String field, Coding coding, java.util.List<FieldError> errors) {
        if (coding == null) {
            return;
        }
        if (coding.system() != CodingSystem.SNOMED) {
            errors.add(new FieldError(field + ".system", "Dozwolony jest wylacznie system SNOMED (SCTID)",
                    "unsupportedSystem"));
            return;
        }
        if (!SCTID.matcher(coding.code().strip()).matches()) {
            errors.add(new FieldError(field + ".code", "SCTID musi miec 6-18 cyfr", "invalidSctid"));
        }
    }
}
