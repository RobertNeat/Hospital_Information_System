package robert_neat.ereceipt.fhir;

import org.springframework.http.MediaType;

/** Identyfikatory i rozszerzenia FHIR wspolne z his_backend (kontrakt w docs/his_backend_contract). */
public final class FhirSystems {

    /** `MedicationRequest.identifier`: id recepty w HIS. */
    public static final String HIS_PRESCRIPTION_ID = "urn:his:prescription-id";
    /** `MedicationRequest.identifier`: klucz e-recepty nadany przez e-receipt. */
    public static final String ERX_KEY = "urn:his:erx-key";
    /** `MedicationRequest.identifier`: 4-cyfrowy kod dostepu. */
    public static final String ACCESS_CODE = "urn:his:access-code";
    /** Rozszerzenie z dokladnym statusem recepty (kod `wire` HIS), bo `partially_dispensed` nie ma statusu R4. */
    public static final String STATUS_EXTENSION = "urn:his:fhir:prescription-status";

    public static final String FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType FHIR_JSON = MediaType.valueOf(FHIR_JSON_VALUE);

    private FhirSystems() {
    }
}
