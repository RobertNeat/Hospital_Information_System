package robert_neat.his_backend.terminology.snomed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SpecializationEclResolverTest {

    private static SpecializationEclResolver resolver;

    /** Wiaze prawdziwy application.properties, wiec test pilnuje takze konfiguracji produkcyjnej. */
    @BeforeAll
    static void bindApplicationProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = SpecializationEclResolverTest.class.getResourceAsStream("/application.properties");
                InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(r);
        }
        SuggestionProperties bound = new Binder(new MapConfigurationPropertySource(props))
                .bind("his.terminology.suggestions", SuggestionProperties.class).get();
        resolver = new SpecializationEclResolver(bound);
    }

    @Test
    void matchesSpecializationIgnoringCaseAndDiacritics() {
        assertThat(resolver.resolve("Choroby wewnętrzne", TerminologyKind.PROCEDURE))
                .isEqualTo("<< 386053000 OR << 387713003");
        assertThat(resolver.resolve("  CHIRURGIA   ogólna ", TerminologyKind.PROCEDURE)).isEqualTo("<< 387713003");
        assertThat(resolver.resolve("Kardiologia", TerminologyKind.DIAGNOSIS))
                .isEqualTo("<< 404684003 : 363698007 = << 113257007");
    }

    @Test
    void differentKindsGiveDifferentEcl() {
        assertThat(resolver.resolve("Radiologia", TerminologyKind.PROCEDURE)).isEqualTo("<< 363679005");
        assertThat(resolver.resolve("Radiologia", TerminologyKind.SYMPTOM)).isEqualTo("<< 418799008");
    }

    @Test
    void unknownOrEmptySpecializationUsesFallback() {
        for (String s : new String[] {null, "", "  ", "Okulistyka"}) {
            assertThat(resolver.resolve(s, TerminologyKind.DIAGNOSIS)).isEqualTo("<< 64572001");
            assertThat(resolver.resolve(s, TerminologyKind.SYMPTOM)).isEqualTo("<< 418799008");
            assertThat(resolver.resolve(s, TerminologyKind.PROCEDURE)).isEqualTo("<< 71388002");
        }
    }

    @Test
    void emptyConfigurationFallsBackToBuiltinHierarchies() {
        SpecializationEclResolver empty = new SpecializationEclResolver(new SuggestionProperties(null, null));
        assertThat(empty.resolve("Kardiologia", TerminologyKind.DIAGNOSIS))
                .isEqualTo(SpecializationEclResolver.BUILTIN_DIAGNOSIS);
    }

    @Test
    void everyMockAndDemoSpecializationHasConfiguredProfile() {
        for (String s : new String[] {"Choroby wewnętrzne", "Kardiologia", "Chirurgia ogólna", "Neurologia",
                "Medycyna ratunkowa", "Medycyna rodzinna", "Radiologia", "Diagnostyka laboratoryjna"}) {
            for (TerminologyKind k : TerminologyKind.values()) {
                assertThat(resolver.resolve(s, k)).isNotBlank();
            }
        }
        // profil, a nie fallback: kardiologia i radiologia roznia sie od zestawu domyslnego
        assertThat(resolver.resolve("Neurologia", TerminologyKind.PROCEDURE)).contains("21483005");
    }
}
