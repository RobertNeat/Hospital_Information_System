package robert_neat.eimaging.fhir;

import java.net.URI;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import robert_neat.eimaging.order.ImagingOrder;
import robert_neat.eimaging.order.ImagingOrderService;

/**
 * FHIR R4 `ServiceRequest`: przyjecie zlecenia z HIS (POST; `id` = identyfikator zlecenia HIS), odczyt oraz zmiana
 * stanu zainicjowana przez HIS (PUT, np. anulowanie w HIS). Cialo i odpowiedz to JSON (`application/fhir+json` lub
 * `application/json`) obslugiwany przez HAPI, a nie przez Jacksona; bledy: `OperationOutcome`.
 */
@RestController
@RequestMapping(value = "/fhir/ServiceRequest",
        consumes = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class ServiceRequestController {

    private final ImagingOrderService orders;
    private final ServiceRequestMapper mapper;

    ServiceRequestController(ImagingOrderService orders, ServiceRequestMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    /** 201 dla nowego zlecenia, 200 gdy to samo zlecenie z HIS jest juz zarejestrowane (idempotencja). */
    @PostMapping
    ResponseEntity<String> create(@RequestBody String body) {
        ImagingOrderService.Registration registration = orders.register(mapper.toDraft(mapper.parse(body)));
        ImagingOrder order = registration.order();
        ResponseEntity.BodyBuilder response = registration.created()
                ? ResponseEntity.created(URI.create("/fhir/ServiceRequest/" + order.getHisOrderId()))
                : ResponseEntity.ok();
        return response.contentType(FhirSystems.FHIR_JSON).body(encode(order));
    }

    @GetMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<String> read(@PathVariable String id) {
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON).body(encode(orders.get(id)));
    }

    /** Zmiana stanu z HIS; bez wywolania zwrotnego do HIS. 409 dla niedozwolonego przejscia. */
    @PutMapping("/{id}")
    ResponseEntity<String> update(@PathVariable String id, @RequestBody String body) {
        ServiceRequest resource = mapper.parse(body);
        ImagingOrder updated = orders.changeFromHis(id, mapper.statusOf(resource));
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON).body(encode(updated));
    }

    private String encode(ImagingOrder order) {
        return mapper.encode(mapper.toResource(order, order.getStatus()));
    }
}
