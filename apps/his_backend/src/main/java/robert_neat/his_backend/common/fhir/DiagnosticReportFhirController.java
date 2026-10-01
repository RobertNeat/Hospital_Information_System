package robert_neat.his_backend.common.fhir;

import java.net.URI;
import java.util.List;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import ca.uhn.fhir.context.FhirContext;

/**
 * FHIR R4 `DiagnosticReport` (wynik laboratoryjny albo obrazowy) od usług `e-*`: `POST` zapisuje wynik (201; 200 dla
 * powtorzonego raportu) w module wskazanym systemem kodu badania ({@link FhirOrderHandler#handlesReport}), `GET /{id}`
 * zwraca zapisany wynik z dowolnego modulu. Autoryzacja kluczem uslugowym.
 */
@FhirEndpoint
@RequestMapping(value = "/fhir/DiagnosticReport",
        consumes = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class DiagnosticReportFhirController {

    private final List<FhirOrderHandler> handlers;
    private final FhirContext fhir;

    DiagnosticReportFhirController(List<FhirOrderHandler> handlers, FhirContext fhir) {
        this.handlers = handlers;
        this.fhir = fhir;
    }

    @PostMapping
    ResponseEntity<String> create(@RequestBody String body) {
        DiagnosticReport report;
        try {
            report = fhir.newJsonParser().parseResource(DiagnosticReport.class, body);
        } catch (RuntimeException e) {
            throw FhirException.invalid("Niepoprawny zasob DiagnosticReport (JSON R4): " + e.getMessage());
        }
        FhirOrderHandler handler = handlers.stream().filter(h -> h.handlesReport(report)).findFirst()
                .orElseThrow(() -> FhirException.invalid("Brak kodu badania (" + FhirSystems.LAB_TEST + " albo "
                        + FhirSystems.IMAGING_EXAM + ")"));
        FhirOrderHandler.Recorded recorded = handler.recordReport(report);
        String id = recorded.report().getIdElement().getIdPart();
        ResponseEntity.BodyBuilder response = recorded.created()
                ? ResponseEntity.created(URI.create("/fhir/DiagnosticReport/" + id))
                : ResponseEntity.ok();
        return response.contentType(FhirSystems.FHIR_JSON)
                .body(fhir.newJsonParser().encodeResourceToString(recorded.report()));
    }

    @GetMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<String> read(@PathVariable String id) {
        DiagnosticReport report = handlers.stream().map(h -> h.findReport(id)).flatMap(java.util.Optional::stream)
                .findFirst().orElseThrow(() -> FhirException.notFound("Wynik '" + id + "' nie istnieje"));
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON)
                .body(fhir.newJsonParser().encodeResourceToString(report));
    }
}
