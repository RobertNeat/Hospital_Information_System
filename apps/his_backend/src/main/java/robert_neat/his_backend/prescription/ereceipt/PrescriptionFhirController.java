package robert_neat.his_backend.prescription.ereceipt;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import robert_neat.his_backend.common.fhir.FhirEndpoint;
import robert_neat.his_backend.common.fhir.FhirSystems;

/**
 * FHIR R4 `MedicationRequest` wystawiony dla e-receipt: `PUT /fhir/MedicationRequest/{id}` (id = identyfikator
 * recepty w HIS) zmienia stan recepty, `GET` zwraca ja jako zasob. Autoryzacja kluczem uslugowym
 * ({@link robert_neat.his_backend.common.fhir.FhirServiceProperties}); tresc obsluguje HAPI, nie Jackson.
 */
@FhirEndpoint
@RequestMapping(value = "/fhir/MedicationRequest",
        consumes = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class PrescriptionFhirController {

    private final PrescriptionExternalService service;
    private final PrescriptionFhirMapper mapper;

    PrescriptionFhirController(PrescriptionExternalService service, PrescriptionFhirMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<String> read(@PathVariable String id) {
        return json(mapper.encode(service.read(id)));
    }

    @PutMapping("/{id}")
    ResponseEntity<String> update(@PathVariable String id, @RequestBody String body) {
        return json(mapper.encode(service.applyStatus(id, mapper.parse(body))));
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON).body(body);
    }
}
