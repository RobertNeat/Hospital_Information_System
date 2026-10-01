package robert_neat.his_backend.common.fhir;

import org.springframework.http.MediaType;

/** Stale FHIR wspolne dla integracji z usługami e-* (kontrakt: docs/his_backend_contract/rest-api-fhir.md). */
public final class FhirSystems {

    /** `MedicationRequest.identifier`: id recepty w HIS. */
    public static final String HIS_PRESCRIPTION_ID = "urn:his:prescription-id";
    /** `MedicationRequest.identifier`: klucz e-recepty nadany przez e-receipt. */
    public static final String ERX_KEY = "urn:his:erx-key";
    /** `MedicationRequest.identifier`: 4-cyfrowy kod dostepu. */
    public static final String ACCESS_CODE = "urn:his:access-code";
    /** Rozszerzenie z dokladnym statusem recepty HIS (`partially_dispensed` nie ma statusu R4). */
    public static final String STATUS_EXTENSION = "urn:his:fhir:prescription-status";

    /** Naglowek klucza uslugowego (docelowo zastapiony przez mTLS). */
    public static final String SERVICE_KEY_HEADER = "X-Service-Key";

    public static final String FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType FHIR_JSON = MediaType.valueOf(FHIR_JSON_VALUE);

    private FhirSystems() {
    }
}
