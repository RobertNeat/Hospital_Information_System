package robert_neat.his_backend.common.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fabryka `ProblemDetail` (RFC 9457) w ksztalcie kontraktu: type/title/status/detail/instance
 * oraz rozszerzenia `code` i `errors[]`.
 */
public final class ApiProblem {

    public static final String PROPERTY_CODE = "code";
    public static final String PROPERTY_ERRORS = "errors";

    private ApiProblem() {
    }

    public static ProblemDetail of(HttpStatusCode status, ApiErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        if (status instanceof HttpStatus hs) {
            pd.setTitle(hs.getReasonPhrase());
        }
        if (code != null) {
            pd.setType(typeOf(code));
            pd.setProperty(PROPERTY_CODE, code);
        }
        return pd;
    }

    public static ProblemDetail validation(String detail, List<FieldError> errors) {
        ProblemDetail pd = of(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED, detail);
        pd.setProperty(PROPERTY_ERRORS, errors);
        return pd;
    }

    /** Domyslny kod dla statusu HTTP (null, gdy kontrakt nie przewiduje kodu, np. 405/415). */
    public static ApiErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ApiErrorCode.UNAUTHENTICATED;
            case 403 -> ApiErrorCode.FORBIDDEN;
            case 404 -> ApiErrorCode.NOT_FOUND;
            case 409 -> ApiErrorCode.CONFLICT;
            case 422 -> ApiErrorCode.VALIDATION_FAILED;
            default -> status.is5xxServerError() ? ApiErrorCode.INTERNAL : null;
        };
    }

    static URI typeOf(ApiErrorCode code) {
        return URI.create("urn:his:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
