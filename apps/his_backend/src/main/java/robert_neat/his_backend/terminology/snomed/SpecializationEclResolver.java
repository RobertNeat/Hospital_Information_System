package robert_neat.his_backend.terminology.snomed;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Wybiera ECL dla specjalizacji lekarza. Brak profilu, pusta specjalizacja lub brak ECL danego rodzaju w profilu
 * -> zestaw domyslny (`fallback`); gdy i on pusty, szeroka hierarchia SNOMED danego rodzaju.
 */
@Component
public class SpecializationEclResolver {

    static final String BUILTIN_DIAGNOSIS = "<< 64572001";
    static final String BUILTIN_SYMPTOM = "<< 418799008";
    static final String BUILTIN_PROCEDURE = "<< 71388002";

    private final SuggestionProperties properties;

    public SpecializationEclResolver(SuggestionProperties properties) {
        this.properties = properties;
    }

    public String resolve(String specialization, TerminologyKind kind) {
        String normalized = normalize(specialization);
        if (!normalized.isEmpty()) {
            for (SuggestionProperties.Profile p : properties.profiles()) {
                boolean matches = p.specializations().stream().anyMatch(s -> normalize(s).equals(normalized));
                if (matches && notBlank(p.eclSet().forKind(kind))) {
                    return p.eclSet().forKind(kind).strip();
                }
            }
        }
        String fallback = properties.fallback().forKind(kind);
        if (notBlank(fallback)) {
            return fallback.strip();
        }
        return switch (kind) {
            case DIAGNOSIS -> BUILTIN_DIAGNOSIS;
            case SYMPTOM -> BUILTIN_SYMPTOM;
            case PROCEDURE -> BUILTIN_PROCEDURE;
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.strip().toLowerCase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").replaceAll("\\s+", " ");
    }
}
