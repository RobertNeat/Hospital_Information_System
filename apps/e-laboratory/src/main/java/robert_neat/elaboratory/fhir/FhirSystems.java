package robert_neat.elaboratory.fhir;

import org.springframework.http.MediaType;

/** Identyfikatory i rozszerzenia FHIR wspolne z his_backend (kontrakt w docs/his_backend_contract). */
public final class FhirSystems {

    /** `ServiceRequest.identifier`: id zlecenia laboratoryjnego w HIS (to samo co `ServiceRequest.id` w e-laboratory). */
    public static final String HIS_LAB_ORDER_ID = "urn:his:lab-order-id";
    /** Kod badania z katalogu HIS (`orderDetail.coding`, `DiagnosticReport.code.coding`). */
    public static final String LAB_TEST = "urn:his:lab-test";
    /** Kod analitu z katalogu HIS (`Observation.code.coding`). */
    public static final String LAB_ANALYTE = "urn:his:lab-analyte";
    /** Rodzaj materialu pozycji zlecenia (`orderDetail.coding`). */
    public static final String SPECIMEN_TYPE = "urn:his:specimen-type";
    /** Rozszerzenie z dokladnym statusem zlecenia (kod `wire` HIS), bo status R4 nie rozroznia etapow. */
    public static final String LAB_STATUS_EXTENSION = "urn:his:fhir:lab-order-status";
    /** Rozszerzenie `orderDetail` z definicja analitu (podrozszerzenia: code, name, unit, low, high). */
    public static final String LAB_ANALYTE_EXTENSION = "urn:his:fhir:lab-analyte";
    /** HL7 v3 ObservationInterpretation: flaga obserwacji (N, L, H, LL, HH, A). */
    public static final String OBSERVATION_INTERPRETATION =
            "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";

    public static final String FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType FHIR_JSON = MediaType.valueOf(FHIR_JSON_VALUE);

    private FhirSystems() {
    }
}
