package robert_neat.his_backend.common.fhir;

import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.http.HttpStatus;

/** Blad warstwy FHIR: kod HTTP + typ `OperationOutcome.issue.code`. */
public class FhirException extends RuntimeException {

    private final HttpStatus status;
    private final IssueType type;

    public FhirException(HttpStatus status, IssueType type, String message) {
        super(message);
        this.status = status;
        this.type = type;
    }

    public HttpStatus status() {
        return status;
    }

    public IssueType type() {
        return type;
    }

    public static FhirException invalid(String message) {
        return new FhirException(HttpStatus.BAD_REQUEST, IssueType.INVALID, message);
    }

    public static FhirException notFound(String message) {
        return new FhirException(HttpStatus.NOT_FOUND, IssueType.NOTFOUND, message);
    }

    public static FhirException conflict(String message) {
        return new FhirException(HttpStatus.CONFLICT, IssueType.CONFLICT, message);
    }

    public static FhirException unprocessable(String message) {
        return new FhirException(HttpStatus.UNPROCESSABLE_ENTITY, IssueType.BUSINESSRULE, message);
    }
}
