package robert_neat.his_backend.terminology.snomed;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapowanie specjalizacja lekarza -> ECL (osobno rozpoznania, objawy, procedury). Wylacznie wyrazenia ECL
 * oparte o SCTID (bez tresci licencjonowanej). `specializations` to nazwy znormalizowane (male litery, bez
 * znakow diakrytycznych), np. `choroby wewnetrzne`.
 */
@ConfigurationProperties(prefix = "his.terminology.suggestions")
public record SuggestionProperties(EclSet fallback, List<Profile> profiles) {

    public record EclSet(String diagnosis, String symptom, String procedure) {

        String forKind(TerminologyKind kind) {
            return switch (kind) {
                case DIAGNOSIS -> diagnosis;
                case SYMPTOM -> symptom;
                case PROCEDURE -> procedure;
            };
        }
    }

    public record Profile(List<String> specializations, String diagnosis, String symptom, String procedure) {

        public Profile {
            specializations = specializations == null ? List.of() : List.copyOf(specializations);
        }

        EclSet eclSet() {
            return new EclSet(diagnosis, symptom, procedure);
        }
    }

    public SuggestionProperties {
        fallback = fallback == null ? new EclSet(null, null, null) : fallback;
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
    }
}
