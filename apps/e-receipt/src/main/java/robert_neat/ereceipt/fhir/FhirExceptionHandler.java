package robert_neat.ereceipt.fhir;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ca.uhn.fhir.context.FhirContext;
import robert_neat.ereceipt.prescription.ReceiptException;

/** Bledy warstwy FHIR jako `OperationOutcome` (zamiast domyslnego JSON Springa). */
@RestControllerAdvice(assignableTypes = MedicationRequestController.class)
class FhirExceptionHandler {

    private final FhirContext fhir;

    FhirExceptionHandler(FhirContext fhir) {
        this.fhir = fhir;
    }

    @ExceptionHandler(ReceiptException.class)
    ResponseEntity<String> receipt(ReceiptException e) {
        return switch (e.kind()) {
            case NOT_FOUND -> outcome(HttpStatus.NOT_FOUND, IssueType.NOTFOUND, e.getMessage());
            case CONFLICT -> outcome(HttpStatus.CONFLICT, IssueType.CONFLICT, e.getMessage());
            case INVALID -> outcome(HttpStatus.BAD_REQUEST, IssueType.INVALID, e.getMessage());
            case REJECTED_BY_HIS -> outcome(HttpStatus.BAD_GATEWAY, IssueType.PROCESSING, e.getMessage());
        };
    }

    private ResponseEntity<String> outcome(HttpStatus status, IssueType type, String message) {
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(IssueSeverity.ERROR).setCode(type).setDiagnostics(message);
        return ResponseEntity.status(status).contentType(FhirSystems.FHIR_JSON)
                .body(fhir.newJsonParser().encodeResourceToString(oo));
    }
}
