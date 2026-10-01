package robert_neat.ereceipt.fhir;

import java.net.URI;

import org.hl7.fhir.r4.model.MedicationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import robert_neat.ereceipt.prescription.Receipt;
import robert_neat.ereceipt.prescription.ReceiptService;

/**
 * FHIR R4 `MedicationRequest`: przyjecie recepty z HIS (POST, zwraca klucz `eRxKey` jako `id` i identyfikator
 * {@link FhirSystems#ERX_KEY}), odczyt oraz zmiana stanu zainicjowana przez HIS (PUT, np. anulowanie w HIS).
 * Cialo i odpowiedz to JSON (`application/fhir+json` lub `application/json`) obslugiwany przez HAPI,
 * a nie przez Jacksona; bledy: `OperationOutcome` ({@link FhirExceptionHandler}).
 */
@RestController
@RequestMapping(value = "/fhir/MedicationRequest",
        consumes = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
        produces = {FhirSystems.FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
class MedicationRequestController {

    private final ReceiptService receipts;
    private final MedicationRequestMapper mapper;

    MedicationRequestController(ReceiptService receipts, MedicationRequestMapper mapper) {
        this.receipts = receipts;
        this.mapper = mapper;
    }

    /** 201 dla nowej recepty, 200 gdy ta sama recepta z HIS jest juz zarejestrowana (idempotencja). */
    @PostMapping
    ResponseEntity<String> create(@RequestBody String body) {
        ReceiptService.Registration registration =
                receipts.register(mapper.toDraft(mapper.parse(body)));
        Receipt receipt = registration.receipt();
        ResponseEntity.BodyBuilder response = registration.created()
                ? ResponseEntity.created(URI.create("/fhir/MedicationRequest/" + receipt.getErxKey()))
                : ResponseEntity.ok();
        return response.contentType(FhirSystems.FHIR_JSON).body(encode(receipt));
    }

    @GetMapping(value = "/{id}", consumes = MediaType.ALL_VALUE)
    ResponseEntity<String> read(@PathVariable String id) {
        return ResponseEntity.ok().contentType(FhirSystems.FHIR_JSON).body(encode(receipts.get(id)));
    }

    /** Zmiana stanu z HIS; bez wywolania zwrotnego do HIS. 409 dla niedozwolonego przejscia. */
    @PutMapping("/{id}")
    ResponseEntity<String> update(@PathVariable String id, @RequestBody String body) {
        MedicationRequest resource = mapper.parse(body);
        Receipt updated = receipts.changeFromHis(id, mapper.statusOf(resource));
        return ResponseEntity.status(HttpStatus.OK).contentType(FhirSystems.FHIR_JSON).body(encode(updated));
    }

    private String encode(Receipt receipt) {
        return mapper.encode(mapper.toResource(receipt, receipt.getStatus()));
    }
}
