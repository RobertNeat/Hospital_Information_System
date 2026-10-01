package robert_neat.his_backend.common.fhir;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ca.uhn.fhir.context.FhirContext;

/**
 * Bledy kontrolerow {@link FhirEndpoint} jako `OperationOutcome`. Najwyzszy priorytet, zawezony do kontrolerow FHIR,
 * wiec globalny `ProblemDetail` pozostaje dla reszty API.
 */
@RestControllerAdvice(annotations = FhirEndpoint.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class FhirExceptionHandler {

    /** Kontekst z cache HAPI (bez wstrzykiwania: advice jest widoczny tez w testach slice `@WebMvcTest`). */
    private final FhirContext fhir = FhirContext.forR4Cached();

    @ExceptionHandler(FhirException.class)
    ResponseEntity<String> fhirError(FhirException e) {
        return outcome(e.status(), e.type(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<String> optimisticLock(OptimisticLockingFailureException e) {
        return outcome(HttpStatus.CONFLICT, IssueType.CONFLICT, "Zasob zostal zmodyfikowany rownolegle");
    }

    private ResponseEntity<String> outcome(HttpStatus status, IssueType type, String message) {
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue().setSeverity(IssueSeverity.ERROR).setCode(type).setDiagnostics(message);
        return ResponseEntity.status(status).contentType(FhirSystems.FHIR_JSON)
                .body(fhir.newJsonParser().encodeResourceToString(oo));
    }
}
