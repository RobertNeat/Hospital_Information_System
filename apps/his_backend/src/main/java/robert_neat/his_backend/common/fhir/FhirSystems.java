package robert_neat.his_backend.common.fhir;

import org.springframework.http.MediaType;

/** Stale FHIR wspolne dla integracji z usługami e-* (kontrakt: kontrolery w pakiecie fhir). */
public final class FhirSystems {

    /** `MedicationRequest.identifier`: id recepty w HIS. */
    public static final String HIS_PRESCRIPTION_ID = "urn:his:prescription-id";
    /** `MedicationRequest.identifier`: klucz e-recepty nadany przez e-receipt. */
    public static final String ERX_KEY = "urn:his:erx-key";
    /** `MedicationRequest.identifier`: 4-cyfrowy kod dostepu. */
    public static final String ACCESS_CODE = "urn:his:access-code";
    /** Rozszerzenie z dokladnym statusem recepty HIS (`partially_dispensed` nie ma statusu R4). */
    public static final String STATUS_EXTENSION = "urn:his:fhir:prescription-status";

    /** `ServiceRequest.identifier`: id zlecenia laboratoryjnego w HIS (to samo co `ServiceRequest.id` w e-laboratory). */
    public static final String HIS_LAB_ORDER_ID = "urn:his:lab-order-id";
    /** Kod badania z katalogu HIS (`orderDetail.coding`, `DiagnosticReport.code.coding`). */
    public static final String LAB_TEST = "urn:his:lab-test";
    /** Kod analitu z katalogu HIS (`Observation.code.coding`). */
    public static final String LAB_ANALYTE = "urn:his:lab-analyte";
    /** Rodzaj materialu pozycji zlecenia (`orderDetail.coding`). */
    public static final String SPECIMEN_TYPE = "urn:his:specimen-type";
    /** Rozszerzenie z dokladnym statusem zlecenia HIS (status R4 nie rozroznia etapow aktywnego zlecenia). */
    public static final String LAB_STATUS_EXTENSION = "urn:his:fhir:lab-order-status";
    /** Rozszerzenie `orderDetail` z definicja analitu badania (podrozszerzenia: code, name, unit, low, high). */
    public static final String LAB_ANALYTE_EXTENSION = "urn:his:fhir:lab-analyte";
    /** HL7 v3 ObservationInterpretation: flaga obserwacji (N, L, H, LL, HH, A). */
    public static final String OBSERVATION_INTERPRETATION =
            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";

    /** `ServiceRequest.identifier`: id zlecenia obrazowego w HIS (to samo co `ServiceRequest.id` w e-imaging). */
    public static final String HIS_IMAGING_ORDER_ID = "urn:his:imaging-order-id";
    /** Kod badania obrazowego z katalogu HIS (`ServiceRequest.code.coding`, `DiagnosticReport.code.coding`). */
    public static final String IMAGING_EXAM = "urn:his:imaging-exam";
    /** Modalnosc badania (`orderDetail.coding`): USG, RTG, CT, MRI, ... */
    public static final String IMAGING_MODALITY = "urn:his:imaging-modality";
    /** Strona badania (`orderDetail.coding`): left, right, bilateral, na. */
    public static final String IMAGING_LATERALITY = "urn:his:imaging-laterality";
    /** Rozszerzenie z dokladnym statusem zlecenia obrazowego HIS (status R4 nie rozroznia etapow). */
    public static final String IMAGING_STATUS_EXTENSION = "urn:his:fhir:imaging-order-status";
    /** Rozszerzenie `ServiceRequest` (valueBoolean): badanie z kontrastem. */
    public static final String IMAGING_CONTRAST_EXTENSION = "urn:his:fhir:imaging-contrast";
    /** Rozszerzenie `ServiceRequest` (valueString): id zarezerwowanego slotu grafiku. */
    public static final String IMAGING_SLOT_EXTENSION = "urn:his:fhir:imaging-slot";
    /** Rozszerzenie `DiagnosticReport` (valueString): opis badania (R4 nie ma pola na opis inne niz wnioski). */
    public static final String IMAGING_FINDINGS_EXTENSION = "urn:his:fhir:imaging-findings";
    /** Rozszerzenie `DiagnosticReport` (valueBoolean): wynik krytyczny wg radiologa. */
    public static final String IMAGING_CRITICAL_EXTENSION = "urn:his:fhir:imaging-critical";

    public static final String FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType FHIR_JSON = MediaType.valueOf(FHIR_JSON_VALUE);

    private FhirSystems() {
    }
}
