package robert_neat.his_backend.common.fhir;

import java.util.List;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import ca.uhn.fhir.context.FhirContext;

/**
 * FHIR R4 `ServiceRequest` (zlecenie laboratoryjne albo obrazowe) dla usług `e-*`: `PUT /fhir/ServiceRequest/{id}`
 * (id = identyfikator zlecenia w HIS) zmienia stan zlecenia, `GET` zwraca je jako zasob. Zlecenie wskazuje modul, ktory
 * je posiada ({@link FhirOrderHandler#ownsOrder}); nieznane id to 404. Autoryzacja kluczem uslugowym
 * ({@link FhirServiceProperties}); tresc obsluguje HAPI, nie Jackson.
 */
@FhirEndpoint
@RequestMapping(value = "/fhir/ServiceRequest",
        consumes = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ServiceRequestFhirController {

    private final List<FhirOrderHandler> handlers;
    private final FhirContext fhir;

    ServiceRequestFhirController(List<FhirOrderHandler> handlers, FhirContext fhir) {
        this.handlers = handlers;
        this.fhir = fhir;
    }

    @GetMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<String> read(@PathVariable String id) {
        return json(owner(id).readOrder(id));
    }

    @PutMapping("/{id}")
    ResponseEntity<String> update(@PathVariable String id, @RequestBody String body) {
        ServiceRequest request;
        try {
            request = fhir.newJsonParser().parseResource(ServiceRequest.class, body);
        } catch (RuntimeException e) {
            throw FhirException.invalid("Niepoprawny zasob ServiceRequest (JSON R4): " + e.getMessage());
        }
        return json(owner(id).applyOrderStatus(id, request));
    }

    private FhirOrderHandler owner(String id) {
        return handlers.stream().filter(h -> h.ownsOrder(id)).findFirst()
                .orElseThrow(() -> FhirException.notFound("Zlecenie '" + id + "' nie istnieje"));
    }

    private ResponseEntity<String> json(ServiceRequest resource) {
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON)
                .body(fhir.newJsonParser().encodeResourceToString(resource));
    }
}
