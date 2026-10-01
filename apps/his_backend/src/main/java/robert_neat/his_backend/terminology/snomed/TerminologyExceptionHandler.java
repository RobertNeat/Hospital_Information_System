package robert_neat.his_backend.terminology.snomed;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import robert_neat.his_backend.common.api.ApiErrorCode;
import robert_neat.his_backend.common.api.ApiProblem;

/**
 * Mapowanie wyjatkow terminologii na ProblemDetail (RFC 9457). Najwyzszy priorytet: musi wyprzedzic
 * globalny advice z handlerem catch-all (inaczej wyjatki terminologii skonczylyby jako 500).
 */
@RestControllerAdvice(assignableTypes = TerminologyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TerminologyExceptionHandler {

    @ExceptionHandler(TerminologyException.Unavailable.class)
    ProblemDetail unavailable(TerminologyException.Unavailable e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Serwer terminologii niedostepny", e.getMessage());
    }

    @ExceptionHandler(TerminologyException.InvalidRequest.class)
    ProblemDetail invalid(TerminologyException.InvalidRequest e) {
        return problem(HttpStatus.BAD_REQUEST, "Niepoprawne zapytanie terminologiczne", e.getMessage());
    }

    @ExceptionHandler(TerminologyException.NotFound.class)
    ProblemDetail notFound(TerminologyException.NotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Nie znaleziono pojecia", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ApiErrorCode code = status == HttpStatus.NOT_FOUND ? ApiErrorCode.NOT_FOUND : null;
        ProblemDetail pd = ApiProblem.of(status, code, detail);
        pd.setTitle(title);
        return pd;
    }
}
