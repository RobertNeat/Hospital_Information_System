package robert_neat.eimaging.fhir;

import org.springframework.http.MediaType;

/** Identyfikatory i rozszerzenia FHIR wspolne z his_backend (kontrakt: kod his_backend, pakiet fhir). */
public final class FhirSystems {

    /** `ServiceRequest.identifier`: id zlecenia obrazowego w HIS (to samo co `ServiceRequest.id` w e-imaging). */
    public static final String HIS_IMAGING_ORDER_ID = "urn:his:imaging-order-id";
    /** Kod badania obrazowego z katalogu HIS (`ServiceRequest.code.coding`, `DiagnosticReport.code.coding`). */
    public static final String IMAGING_EXAM = "urn:his:imaging-exam";
    /** Modalnosc badania (`orderDetail.coding`): USG, RTG, CT, MRI, ... */
    public static final String IMAGING_MODALITY = "urn:his:imaging-modality";
    /** Strona badania (`orderDetail.coding`): left, right, bilateral, na. */
    public static final String IMAGING_LATERALITY = "urn:his:imaging-laterality";
    /** Rozszerzenie z dokladnym statusem zlecenia (kod `wire` HIS), bo status R4 nie rozroznia etapow. */
    public static final String IMAGING_STATUS_EXTENSION = "urn:his:fhir:imaging-order-status";
    /** Rozszerzenie `ServiceRequest` (valueBoolean): badanie z kontrastem. */
    public static final String IMAGING_CONTRAST_EXTENSION = "urn:his:fhir:imaging-contrast";
    /** Rozszerzenie `ServiceRequest` (valueString): id zarezerwowanego slotu grafiku. */
    public static final String IMAGING_SLOT_EXTENSION = "urn:his:fhir:imaging-slot";
    /** Rozszerzenie `DiagnosticReport` (valueString): opis badania. */
    public static final String IMAGING_FINDINGS_EXTENSION = "urn:his:fhir:imaging-findings";
    /** Rozszerzenie `DiagnosticReport` (valueBoolean): wynik krytyczny wg radiologa. */
    public static final String IMAGING_CRITICAL_EXTENSION = "urn:his:fhir:imaging-critical";

    public static final String FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType FHIR_JSON = MediaType.valueOf(FHIR_JSON_VALUE);

    private FhirSystems() {
    }
}
