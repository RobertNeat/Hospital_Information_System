package robert_neat.his_backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import robert_neat.his_backend.ApiIntegrationTest;

/**
 * Katalogi tylko do odczytu (`/lab-tests`, `/lab-panels`, `/imaging-exams`, `/imaging-slots`, `/drugs`,
 * `/vital-thresholds`) na danych mock/reference: 20 badan lab (3 panele), 12 badan obrazowych, 25 lekow,
 * 3168 slotow (po 32 USG i 16 pozostalych modalnosci na dobe, doby od -7 do +14 dni), 6 progow parametrow.
 */
class CatalogApiTest extends ApiIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String POLPRIL = "24c2f3ff-06d4-53d2-9bc5-561b36774246";
    private static final String METFORMAX = "6ab6949a-fcf3-53bd-a0a7-d01c97995b6f";
    private static final String XARELTO = "1b72160d-900b-5776-a0e1-a81f2bfa8b29";
    private static final String CLEXANE = "59954ea3-2321-530c-a906-c61fb61bb20b";
    private static final String FUROSEMID = "ef77115d-9ec8-5742-9217-3146231204a4";
    private static final String PANEL_PREOP = "ae93171c-dab0-598a-8492-7f4f316b7d38";

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    // --- uwierzytelnienie i uprawnienia ---

    @ParameterizedTest
    @ValueSource(strings = {"/lab-tests", "/lab-panels", "/imaging-exams", "/imaging-slots?modality=USG&date=2026-01-01",
            "/drugs", "/drugs?term=ramipril", "/drugs/" + POLPRIL, "/vital-thresholds"})
    void readsWithoutTokenAreUnauthorized(String path) throws Exception {
        mvc.perform(get("/api/v1" + path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    static Stream<Arguments> permissions() {
        List<String> all = List.of("admin", "doctor", "nurse", "lab-tech", "radiologist", "pharmacist", "registrar");
        List<String> slotsPath = List.of("/imaging-slots?modality=USG&date=2026-01-01");
        return Stream.of(
                        Map.entry("/lab-tests", List.of("doctor", "nurse", "lab-tech", "admin")),
                        Map.entry("/lab-panels", List.of("doctor", "nurse", "lab-tech", "admin")),
                        Map.entry("/imaging-exams", List.of("doctor", "radiologist", "admin")),
                        Map.entry(slotsPath.getFirst(), List.of("doctor", "radiologist", "admin")),
                        Map.entry("/drugs", List.of("doctor", "nurse", "pharmacist", "admin")),
                        Map.entry("/drugs/" + POLPRIL, List.of("doctor", "nurse", "pharmacist", "admin")),
                        Map.entry("/vital-thresholds", List.of("doctor", "nurse", "admin")))
                .flatMap(e -> all.stream().map(login -> Arguments.of(e.getKey(), login, e.getValue().contains(login))));
    }

    @ParameterizedTest(name = "{0} jako {1}: dozwolone={2}")
    @MethodSource("permissions")
    void readsFollowRoleMatrix(String path, String login, boolean allowed) throws Exception {
        ResultActions result = as(login, path);
        if (allowed) {
            result.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(JSON));
        } else {
            result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void catalogsAreReadOnly() throws Exception {
        for (String path : List.of("/lab-tests", "/lab-panels", "/imaging-exams", "/imaging-slots", "/drugs",
                "/vital-thresholds")) {
            mvc.perform(post("/api/v1" + path).header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                    .contentType(JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        }
    }

    // --- laboratorium ---

    @Test
    void labTestsAreListedWithContractShape() throws Exception {
        as("doctor", "/lab-tests")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[*].analytes").exists())
                .andExpect(jsonPath("$[?(@.code=='CRP')].loinc").value("1988-5"))
                .andExpect(jsonPath("$[?(@.code=='CRP')].name").value("CRP"))
                .andExpect(jsonPath("$[?(@.code=='CRP')].category").value("biochemistry"))
                .andExpect(jsonPath("$[?(@.code=='CRP')].defaultSpecimen").value("serum"))
                .andExpect(jsonPath("$[?(@.code=='CRP')].specimenTypes[*]", contains("blood", "serum")))
                .andExpect(jsonPath("$[?(@.code=='CRP')].turnaroundHours").value(2))
                .andExpect(jsonPath("$[?(@.code=='CRP')].fastingRequired").value(false))
                .andExpect(jsonPath("$[?(@.code=='GLU')].fastingRequired").value(true))
                .andExpect(jsonPath("$[?(@.code=='UROC')].category").value("microbiology"))
                .andExpect(jsonPath("$[?(@.code=='UROC')].turnaroundHours").value(48))
                .andExpect(jsonPath("$[?(@.code=='HISTPAT')].category").value("pathology"))
                .andExpect(jsonPath("$[?(@.code=='HISTPAT')].defaultSpecimen").value("tissue"))
                // pola opcjonalne bez wartosci sa pomijane (NON_ABSENT), nie null
                .andExpect(jsonPath("$[?(@.code=='HISTPAT')].loinc").isEmpty())
                .andExpect(jsonPath("$[?(@.code=='MORF')].loinc").value("58410-2"));
    }

    @Test
    void labTestsCarryAnalytesWithReferenceRanges() throws Exception {
        String body = body(as("lab-tech", "/lab-tests"));
        Map<String, Object> morf = one(body, "$[?(@.code=='MORF')]");
        List<Map<String, Object>> analytes = maps(morf.get("analytes"));
        assertThat(analytes).hasSize(5);
        Map<String, Object> wbc = analytes.stream().filter(a -> "WBC".equals(a.get("code"))).findFirst().orElseThrow();
        assertThat(wbc).containsEntry("name", "Leukocyty").containsEntry("unit", "tys/uL")
                .containsEntry("low", 4).containsEntry("high", 10);
        Map<String, Object> rbc = analytes.stream().filter(a -> "RBC".equals(a.get("code"))).findFirst().orElseThrow();
        assertThat(rbc).containsEntry("low", 4.2).containsEntry("high", 5.4);

        // anality wg kodu; wiele analitow na badanie
        assertThat(codes(one(body, "$[?(@.code=='KREA')]"))).containsExactly("EGFR", "KREA");
        assertThat(codes(one(body, "$[?(@.code=='ELEK')]"))).containsExactly("K", "NA");
        Map<String, Object> krea = maps(one(body, "$[?(@.code=='KREA')]").get("analytes")).stream()
                .filter(a -> "KREA".equals(a.get("code"))).findFirst().orElseThrow();
        assertThat(krea).containsEntry("low", 0.6).containsEntry("high", 1.2).containsEntry("unit", "mg/dL");
        // zakres referencyjny dolny rowny 0 jest zwracany (nie jest brakiem)
        Map<String, Object> crp = maps(one(body, "$[?(@.code=='CRP')]").get("analytes")).getFirst();
        assertThat(crp).containsEntry("code", "CRP").containsEntry("low", 0).containsEntry("high", 5);
    }

    @Test
    void labTestsAreSortedByPolishName() throws Exception {
        String body = body(as("doctor", "/lab-tests"));
        List<String> names = JsonPath.read(body, "$[*].name");
        Collator pl = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
        assertThat(names).hasSize(20).isSortedAccordingTo(pl::compare);
    }

    @Test
    void labPanelsListTestCodes() throws Exception {
        String body = body(as("nurse", "/lab-panels"));
        List<Map<String, Object>> panels = JsonPath.read(body, "$");
        assertThat(panels).hasSize(3);
        assertThat(panels).extracting(p -> p.get("name"))
                .containsExactly("Kontrola cukrzycy", "Pakiet przedoperacyjny", "Profil kardiologiczny");
        assertThat(panels).allSatisfy(p -> assertThat(p.keySet()).containsExactlyInAnyOrder("id", "name", "testCodes"));
        Map<String, Object> preop = one(body, "$[?(@.id=='" + PANEL_PREOP + "')]");
        assertThat(preop).containsEntry("name", "Pakiet przedoperacyjny");
        assertThat(preop.get("testCodes")).isEqualTo(List.of("APTT", "ELEK", "INRPT", "KREA", "MORF", "UROG"));
        assertThat(one(body, "$[?(@.name=='Kontrola cukrzycy')]").get("testCodes"))
                .isEqualTo(List.of("GLU", "HBA1C", "KREA", "LIPID"));
        assertThat(list(one(body, "$[?(@.name=='Profil kardiologiczny')]").get("testCodes"))).hasSize(5);
        // identyfikatory paneli to UUID
        as("nurse", "/lab-panels").andExpect(jsonPath("$[*].id", everyItem(matchesPattern(UUID_PATTERN))));
    }

    // --- obrazowanie: badania ---

    @Test
    void imagingExamsAreListedWithContractShape() throws Exception {
        as("radiologist", "/imaging-exams")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].modality").value("USG"))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].modality").value("CT"))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].name").value("TK klatki piersiowej z kontrastem"))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].bodyRegion").value("Klatka piersiowa"))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].contrastPossible").value(true))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].requiresLaterality").value(false))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].preparation").value("Bycie na czczo 4h przed badaniem."))
                .andExpect(jsonPath("$[?(@.code=='TK-KL-K')].durationMinutes").value(20))
                .andExpect(jsonPath("$[?(@.code=='RTG-KOL')].requiresLaterality").value(true))
                .andExpect(jsonPath("$[?(@.code=='USG-JB')].preparation").isEmpty())
                .andExpect(jsonPath("$[?(@.code=='MMG')].modality").value("MMG"))
                .andExpect(jsonPath("$[?(@.code=='GASTRO')].modality").value("ENDOSCOPY"))
                .andExpect(jsonPath("$[?(@.code=='KOLONO')].modality").value("COLONOSCOPY"))
                .andExpect(jsonPath("$[?(@.code=='KORONARO')].modality").value("ANGIOGRAPHY"))
                .andExpect(jsonPath("$[?(@.code=='RM-LS')].modality").value("MRI"));
    }

    @Test
    void imagingExamsAreSortedByModalityThenName() throws Exception {
        as("doctor", "/imaging-exams")
                .andExpect(jsonPath("$[*].code", contains("USG-JB", "USG-TR", "RTG-KL", "RTG-KOL", "TK-GL",
                        "TK-KL-K", "RM-GL-K", "RM-LS", "MMG", "GASTRO", "KOLONO", "KORONARO")));
    }

    @Test
    void imagingExamsFilterByModality() throws Exception {
        as("doctor", "/imaging-exams?modality=USG")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].modality", contains("USG", "USG")));
        as("doctor", "/imaging-exams?modality=CT")
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("TK-GL", "TK-KL-K")));
        as("doctor", "/imaging-exams?modality=MRI").andExpect(jsonPath("$", hasSize(2)));
        as("doctor", "/imaging-exams?modality=MMG").andExpect(jsonPath("$[0].code").value("MMG"));
        as("doctor", "/imaging-exams?modality=RTG").andExpect(jsonPath("$", hasSize(2)));
        as("doctor", "/imaging-exams?modality=ANGIOGRAPHY").andExpect(jsonPath("$", hasSize(1)));
        as("doctor", "/imaging-exams?modality=").andExpect(jsonPath("$", hasSize(12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"usg", "TK", "xyz"})
    void imagingExamsRejectUnknownModality(String modality) throws Exception {
        as("doctor", "/imaging-exams?modality=" + modality)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("modality"));
    }

    // --- obrazowanie: sloty ---

    @Test
    void slotsOfTodayHaveContractShape() throws Exception {
        LocalDate today = LocalDate.now(ImagingCatalogService.CLINIC_ZONE);
        ZonedDateTime firstStart = today.atTime(8, 0).atZone(ImagingCatalogService.CLINIC_ZONE);
        as("radiologist", "/imaging-slots?modality=USG&date=" + today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(32)))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].modality").value("USG"))
                .andExpect(jsonPath("$[0].start").value(firstStart.toInstant().toString()))
                .andExpect(jsonPath("$[0].end").value(firstStart.plusMinutes(30).toInstant().toString()))
                .andExpect(jsonPath("$[0].room").value("USG-1"))
                .andExpect(jsonPath("$[0].available").isBoolean())
                .andExpect(jsonPath("$[*].start", everyItem(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z"))))
                .andExpect(jsonPath("$[*].room", containsInAnyOrder(
                        Stream.concat(Stream.generate(() -> "USG-1").limit(16), Stream.generate(() -> "USG-2").limit(16))
                                .toArray())));
        as("doctor", "/imaging-slots?modality=RTG&date=" + today)
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0].room").value("RTG-1"));
    }

    @Test
    void slotsIncludeUnavailableOnesWithFlag() throws Exception {
        LocalDate today = LocalDate.now(ImagingCatalogService.CLINIC_ZONE);
        String body = body(as("doctor", "/imaging-slots?modality=USG&date=" + today));
        List<Boolean> flags = JsonPath.read(body, "$[*].available");
        assertThat(flags).contains(true, false);
        int unavailable = jdbc.queryForObject("""
                SELECT count(*) FROM schedule_slot
                WHERE modality = 'USG' AND NOT available
                  AND (start_at AT TIME ZONE 'Europe/Warsaw')::date = ?""", Integer.class, today);
        assertThat(flags.stream().filter(f -> !f).count()).isEqualTo(unavailable).isPositive();
    }

    @Test
    void slotsAreSelectedByCalendarDayInClinicTimeZone() throws Exception {
        LocalDate today = LocalDate.now(ImagingCatalogService.CLINIC_ZONE);
        for (int offset : List.of(-7, -1, 0, 1, 14)) {
            LocalDate day = today.plusDays(offset);
            String body = body(as("doctor", "/imaging-slots?modality=CT&date=" + day));
            List<String> ids = JsonPath.read(body, "$[*].id");
            List<String> expected = jdbc.queryForList("""
                    SELECT cast(id AS text) FROM schedule_slot
                    WHERE modality = 'CT' AND (start_at AT TIME ZONE 'Europe/Warsaw')::date = ?""", String.class, day);
            assertThat(ids).as("doba %s", day).hasSize(16).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void slotsAreOrderedByStart() throws Exception {
        LocalDate today = LocalDate.now(ImagingCatalogService.CLINIC_ZONE);
        List<String> starts = JsonPath.read(body(as("doctor", "/imaging-slots?modality=USG&date=" + today)),
                "$[*].start");
        assertThat(starts).isSorted();
    }

    @Test
    void slotsOutsideGeneratedRangeAreEmpty() throws Exception {
        LocalDate today = LocalDate.now(ImagingCatalogService.CLINIC_ZONE);
        as("doctor", "/imaging-slots?modality=USG&date=" + today.plusDays(15))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        as("doctor", "/imaging-slots?modality=USG&date=" + today.minusDays(8))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void slotsRequireValidModalityAndDate() throws Exception {
        as("doctor", "/imaging-slots?date=2026-01-01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("modality"));
        as("doctor", "/imaging-slots?modality=USG")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        as("doctor", "/imaging-slots?modality=usg&date=2026-01-01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("modality"));
        for (String badDate : List.of("2026-13-45", "01.10.2026", "dzis", "2026-01-01T10:00:00Z")) {
            as("doctor", "/imaging-slots?modality=USG&date=" + badDate)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("date"));
        }
    }

    // --- leki ---

    @Test
    void drugsWithoutTermListWholeCatalogByName() throws Exception {
        String body = body(as("pharmacist", "/drugs"));
        List<String> names = JsonPath.read(body, "$[*].name");
        assertThat(names).hasSize(25).isSorted();
        assertThat(names).first().isEqualTo("Amlozek");
        drugs("pharmacist", "").andExpect(jsonPath("$", hasSize(25)));
        drugs("pharmacist", "  ").andExpect(jsonPath("$", hasSize(25)));
    }

    @Test
    void drugHasContractShape() throws Exception {
        drugs("doctor", "metformax")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(METFORMAX))
                .andExpect(jsonPath("$[0].name").value("Metformax"))
                .andExpect(jsonPath("$[0].activeSubstance").value("Metformina"))
                .andExpect(jsonPath("$[0].atcCode").value("A10BA02"))
                .andExpect(jsonPath("$[0].form").value("tablet"))
                .andExpect(jsonPath("$[0].strength").value("850 mg"))
                .andExpect(jsonPath("$[0].packageSize").value(30))
                .andExpect(jsonPath("$[0].packageUnit").value("tabl."))
                .andExpect(jsonPath("$[0].routes", contains("oral")))
                .andExpect(jsonPath("$[0].defaultDoseUnit").value("mg"))
                .andExpect(jsonPath("$[0].rxOnly").value(true))
                .andExpect(jsonPath("$[0].reimbursementOptions", contains("30%", "R", "none")))
                .andExpect(jsonPath("$[0].maxDailyDose.value").value(3000))
                .andExpect(jsonPath("$[0].maxDailyDose.unit").value("mg"))
                .andExpect(jsonPath("$[0].interactsWithAtc").doesNotExist());
    }

    @Test
    void optionalDrugFieldsAreOmittedWhenAbsent() throws Exception {
        as("doctor", "/drugs/" + POLPRIL)
                .andExpect(jsonPath("$.name").value("Polpril"))
                .andExpect(jsonPath("$.reimbursementOptions", contains("30%", "none")))
                .andExpect(jsonPath("$.maxDailyDose").doesNotExist())
                .andExpect(jsonPath("$.interactsWithAtc").doesNotExist());
    }

    @Test
    void drugsExposeEnumsAndInteractionsOnTheWire() throws Exception {
        as("nurse", "/drugs/" + CLEXANE)
                .andExpect(jsonPath("$.form").value("injection"))
                .andExpect(jsonPath("$.routes", contains("sc")))
                .andExpect(jsonPath("$.reimbursementOptions", contains("100%", "none")))
                .andExpect(jsonPath("$.interactsWithAtc", contains("B01AC06", "B01AF01")));
        as("nurse", "/drugs/" + XARELTO)
                .andExpect(jsonPath("$.reimbursementOptions", contains("30%", "B", "none")))
                .andExpect(jsonPath("$.interactsWithAtc", contains("B01AC06")));
        as("nurse", "/drugs/" + FUROSEMID)
                .andExpect(jsonPath("$.routes", contains("oral", "iv")));
        drugs("nurse", "ventolin")
                .andExpect(jsonPath("$[0].form").value("inhaler"))
                .andExpect(jsonPath("$[0].routes", contains("inhalation")));
        drugs("nurse", "diclac")
                .andExpect(jsonPath("$[0].form").value("ointment"))
                .andExpect(jsonPath("$[0].routes", contains("topical")));
        drugs("nurse", "ibuprom")
                .andExpect(jsonPath("$[0].maxDailyDose.value").value(1200))
                .andExpect(jsonPath("$[0].rxOnly").value(false));
    }

    @Test
    void drugSearchMatchesNameSubstanceAndAtcIgnoringCase() throws Exception {
        drugs("doctor", "POLPRIL").andExpect(jsonPath("$[*].id", contains(POLPRIL)));
        drugs("doctor", "ramipr").andExpect(jsonPath("$[*].id", contains(POLPRIL)));
        drugs("doctor", "RAMIPRIL").andExpect(jsonPath("$[*].id", contains(POLPRIL)));
        drugs("doctor", "c09aa05").andExpect(jsonPath("$[*].id", contains(POLPRIL)));
        drugs("doctor", "C09AA").andExpect(jsonPath("$[*].id", contains(POLPRIL)));
        // wspolna substancja czynna i ATC; wynik wg nazwy
        drugs("doctor", "paracetamol").andExpect(jsonPath("$[*].name", contains("Apap", "Paracetamol")));
        drugs("doctor", "N02BE01").andExpect(jsonPath("$[*].name", contains("Apap", "Paracetamol")));
        drugs("doctor", " paracetamol ").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void drugSearchRequiresEveryTokenToMatch() throws Exception {
        drugs("doctor", "amoksycylina klawulanowy")
                .andExpect(jsonPath("$[*].name", contains("Augmentin")));
        drugs("doctor", "klawulanowy amoksycylina")
                .andExpect(jsonPath("$[*].name", contains("Augmentin")));
        drugs("doctor", "augmentin ramipril").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void drugSearchFoldsPolishDiacritics() throws Exception {
        // zapytanie z polskimi znakami wobec nazw bez nich (i odwrotnie), niezaleznie od wielkosci liter
        drugs("doctor", "ŁEWOTYROKSYNA").andExpect(jsonPath("$[*].name", contains("Letrox")));
        drugs("doctor", "łewotyroksyna").andExpect(jsonPath("$[*].name", contains("Letrox")));
        drugs("doctor", "Zołtar").andExpect(jsonPath("$[*].name", contains("Zoltar")));
        drugs("doctor", "Atorwastątyna").andExpect(jsonPath("$[*].name", contains("Atoris")));
        drugs("doctor", "kwas acetylosalicylówy").andExpect(jsonPath("$[*].name", contains("Polocard")));
    }

    @Test
    void drugSearchFoldsDiacriticsStoredInDatabase() throws Exception {
        jdbc.update("""
                INSERT INTO drug (id, name, active_substance, atc_code, form, strength, package_size, package_unit,
                                  default_dose_unit, rx_only)
                VALUES (?, 'Żołądkówka Łagodna', 'Ćwiartka węgla ŚŹ', 'V03AB99', 'syrup', '1 mg', 1, 'but.', 'mg', FALSE)""",
                UUID.randomUUID());
        for (String term : List.of("zoladkowka", "ZOŁĄDKÓWKA", "lagodna", "ŁAGODNA", "cwiartka", "wegla sz",
                "Ćwiartka Węgla")) {
            drugs("doctor", term)
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Żołądkówka Łagodna"));
        }
    }

    @Test
    void drugSearchTreatsWildcardsLiterally() throws Exception {
        drugs("doctor", "%").andExpect(jsonPath("$", hasSize(0)));
        drugs("doctor", "_").andExpect(jsonPath("$", hasSize(0)));
        drugs("doctor", "p_lpril").andExpect(jsonPath("$", hasSize(0)));
        drugs("doctor", "nie-istnieje").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void drugSearchIsLimitedOnServerSide() throws Exception {
        jdbc.update("""
                INSERT INTO drug (id, name, active_substance, atc_code, form, strength, package_size, package_unit,
                                  default_dose_unit, rx_only)
                SELECT gen_random_uuid(), 'Zzlek ' || lpad(g::text, 3, '0'), 'Substancja testowa', 'V03AB98',
                       'tablet', '1 mg', 1, 'tabl.', 'mg', FALSE
                FROM generate_series(1, 60) g""");
        String body = body(as("doctor", "/drugs"));
        List<String> names = JsonPath.read(body, "$[*].name");
        assertThat(names).hasSize(DrugCatalogService.SEARCH_LIMIT).isSorted();
        drugs("doctor", "zzlek").andExpect(jsonPath("$", hasSize(DrugCatalogService.SEARCH_LIMIT)))
                .andExpect(jsonPath("$[0].name").value("Zzlek 001"));
        drugs("doctor", "V03AB98").andExpect(jsonPath("$", hasSize(DrugCatalogService.SEARCH_LIMIT)));
        drugs("doctor", "Zzlek 060").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void drugByIdReturnsTheDrugAnd404ForUnknown() throws Exception {
        as("pharmacist", "/drugs/" + XARELTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(XARELTO))
                .andExpect(jsonPath("$.name").value("Xarelto"))
                .andExpect(jsonPath("$.activeSubstance").value("Rywaroksaban"));
        as("pharmacist", "/drugs/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        as("pharmacist", "/drugs/to-nie-uuid")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- progi parametrow zyciowych ---

    @Test
    void vitalThresholdsMatchReferenceDataInContractOrder() throws Exception {
        as("nurse", "/vital-thresholds")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].type", contains("systolic", "diastolic", "heartRate", "temperature", "spo2",
                        "respiratoryRate")))
                .andExpect(jsonPath("$[0].label").value("Ciśnienie skurczowe"))
                .andExpect(jsonPath("$[0].unit").value("mmHg"))
                .andExpect(jsonPath("$[0].low").value(90))
                .andExpect(jsonPath("$[0].high").value(140))
                .andExpect(jsonPath("$[0].criticalLow").value(80))
                .andExpect(jsonPath("$[0].criticalHigh").value(180))
                .andExpect(jsonPath("$[0].min").value(40))
                .andExpect(jsonPath("$[0].max").value(260))
                .andExpect(jsonPath("$[2].type").value("heartRate"))
                .andExpect(jsonPath("$[2].unit").value("/min"))
                .andExpect(jsonPath("$[3].label").value("Temperatura"))
                .andExpect(jsonPath("$[3].unit").value("°C"))
                .andExpect(jsonPath("$[3].low").value(36))
                .andExpect(jsonPath("$[3].high").value(37.5))
                .andExpect(jsonPath("$[3].criticalLow").value(35))
                .andExpect(jsonPath("$[3].criticalHigh").value(39.5))
                .andExpect(jsonPath("$[3].min").value(30))
                .andExpect(jsonPath("$[3].max").value(43))
                .andExpect(jsonPath("$[4].label").value("Saturacja SpO₂"))
                .andExpect(jsonPath("$[4].high").value(100))
                .andExpect(jsonPath("$[5].type").value("respiratoryRate"));
    }

    @Test
    void numbersAreSerializedWithoutTrailingZeros() throws Exception {
        String body = body(as("doctor", "/vital-thresholds"));
        assertThat(body).contains("\"low\":90,").contains("\"high\":37.5,").doesNotContain("90.0").doesNotContain("36.0");
    }

    @Test
    void adminCanUpdateVitalThreshold() throws Exception {
        mvc.perform(put("/api/v1/vital-thresholds/heartRate").header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                        .contentType(JSON).content(thresholdBody("Tetno", "/min", 55, 95, 45, 125, 25, 240)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("heartRate"))
                .andExpect(jsonPath("$.label").value("Tetno"))
                .andExpect(jsonPath("$.low").value(55))
                .andExpect(jsonPath("$.high").value(95));
        as("doctor", "/vital-thresholds")
                .andExpect(jsonPath("$[?(@.type=='heartRate')].low").value(55))
                .andExpect(jsonPath("$[?(@.type=='heartRate')].high").value(95));
    }

    @ParameterizedTest
    @ValueSource(strings = {"doctor", "nurse", "lab-tech", "radiologist", "pharmacist", "registrar"})
    void onlyAdminCanUpdateVitalThreshold(String login) throws Exception {
        mvc.perform(put("/api/v1/vital-thresholds/heartRate").header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .contentType(JSON).content(thresholdBody("Tetno", "/min", 55, 95, 45, 125, 25, 240)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingUnknownVitalThresholdTypeIs404() throws Exception {
        mvc.perform(put("/api/v1/vital-thresholds/bogus").header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                        .contentType(JSON).content(thresholdBody("X", "x", 1, 2, 0, 3, 0, 4)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updatingVitalThresholdWithInvalidOrderIs422() throws Exception {
        // high < low narusza wymagana kolejnosc progow
        mvc.perform(put("/api/v1/vital-thresholds/heartRate").header(HttpHeaders.AUTHORIZATION, bearer("admin"))
                        .contentType(JSON).content(thresholdBody("Tetno", "/min", 100, 90, 45, 125, 25, 240)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("high"));
    }

    // --- pomocnicze ---

    private static String thresholdBody(String label, String unit, double low, double high, double criticalLow,
            double criticalHigh, double min, double max) {
        return "{\"label\":\"" + label + "\",\"unit\":\"" + unit + "\",\"low\":" + low + ",\"high\":" + high
                + ",\"criticalLow\":" + criticalLow + ",\"criticalHigh\":" + criticalHigh + ",\"min\":" + min
                + ",\"max\":" + max + "}";
    }

    private ResultActions as(String login, String path) throws Exception {
        return mvc.perform(get("/api/v1" + path).header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    private ResultActions drugs(String login, String term) throws Exception {
        return mvc.perform(get("/api/v1/drugs").param("term", term).header(HttpHeaders.AUTHORIZATION, bearer(login)));
    }

    private static Map<String, Object> one(String body, String path) {
        List<Map<String, Object>> found = JsonPath.read(body, path);
        assertThat(found).as(path).hasSize(1);
        return found.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    private static List<Object> codes(Map<String, Object> test) {
        List<Map<String, Object>> analytes = maps(test.get("analytes"));
        return analytes.stream().map(a -> a.get("code")).toList();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String login) throws Exception {
        String token = TOKENS.get(login);
        if (token == null) {
            String response = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"employeeId\":\"" + login + "\",\"password\":\"" + login + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            token = JsonPath.read(response, "$.accessToken");
            TOKENS.put(login, token);
        }
        return "Bearer " + token;
    }
}
