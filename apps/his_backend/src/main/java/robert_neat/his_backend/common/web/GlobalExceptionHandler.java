package robert_neat.his_backend.common.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import robert_neat.his_backend.common.api.ApiErrorCode;
import robert_neat.his_backend.common.api.ApiProblem;
import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnums;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Globalne mapowanie wyjatkow na `ProblemDetail` (RFC 9457) z rozszerzeniami `code` i `errors[]`.
 * Dziedziczy po {@link ResponseEntityExceptionHandler}, zeby wyjatki frameworka (404 brak zasobu, 405, 415...)
 * mialy ten sam ksztalt. Najnizszy priorytet: advice zawezone do kontrolerow (np. terminologia) wygrywaja.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UNIQUE_VIOLATION = "23505";

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    // --- 404 / 409 ---

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> optimisticLock(Exception e) {
        return respond(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
                "Zasob zostal zmodyfikowany przez inna osobe; odswiez dane i sprobuj ponownie");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrity(DataIntegrityViolationException e) {
        if (UNIQUE_VIOLATION.equals(sqlState(e))) {
            return respond(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, "Rekord o podanych danych juz istnieje");
        }
        return internal(e);
    }

    // --- 422 ---

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<ProblemDetail> validationFailed(ValidationFailedException e) {
        return validation(e.getErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraintViolation(ConstraintViolationException e) {
        List<FieldError> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            errors.add(new FieldError(lastNode(v.getPropertyPath()), v.getMessage(),
                    v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()));
        }
        return validation(errors);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(new FieldError(fe.getField(), fe.getDefaultMessage(), fe.getCode())));
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(new FieldError(ge.getObjectName(), ge.getDefaultMessage(), ge.getCode())));
        return validationEntity(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors pe) {
                pe.getFieldErrors().forEach(fe ->
                        errors.add(new FieldError(fe.getField(), fe.getDefaultMessage(), fe.getCode())));
                pe.getGlobalErrors().forEach(ge ->
                        errors.add(new FieldError(ge.getObjectName(), ge.getDefaultMessage(), ge.getCode())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(re -> errors.add(new FieldError(name, re.getDefaultMessage())));
            }
        }
        return validationEntity(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        String message = "Niepoprawna wartosc '" + ex.getValue() + "'";
        Class<?> required = ex.getRequiredType();
        if (required != null && required.isEnum() && WireEnum.class.isAssignableFrom(required)) {
            message += "; dozwolone: " + allowedValues(required);
        }
        return validationEntity(ex, List.of(new FieldError(field, message, "typeMismatch")), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationEntity(ex,
                List.of(new FieldError(ex.getParameterName(), "Parametr jest wymagany", "required")), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        if (findCause(ex, JacksonException.class) instanceof JacksonException je) {
            String field = path(je);
            if (je instanceof InvalidFormatException ife) {
                String message = "Niepoprawna wartosc '" + ife.getValue() + "'";
                Class<?> target = ife.getTargetType();
                if (target != null && target.isEnum() && WireEnum.class.isAssignableFrom(target)) {
                    message += "; dozwolone: " + allowedValues(target);
                }
                errors.add(new FieldError(field, message, "invalidFormat"));
            } else if (je instanceof tools.jackson.databind.exc.MismatchedInputException && !field.isEmpty()) {
                errors.add(new FieldError(field, "Niepoprawny typ lub brakujaca wartosc", "mismatchedInput"));
            }
        }
        ProblemDetail pd = ApiProblem.validation("Tresc zadania jest niepoprawna lub nieczytelna", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Blad serwera przy obsludze zadania", ex);
        }
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            ApiErrorCode code = ApiProblem.codeFor(statusCode);
            if (code != null && (pd.getProperties() == null || !pd.getProperties().containsKey(ApiProblem.PROPERTY_CODE))) {
                pd.setProperty(ApiProblem.PROPERTY_CODE, code);
            }
        }
        return response;
    }

    // --- 401 / 403 ---

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException e) {
        // Brak sesji (anonim) to 401, a nie 403 - istotne dla autoryzacji metod (@PreAuthorize).
        if (trustResolver.isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
            return unauthenticated();
        }
        return respond(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Brak uprawnien do wykonania operacji");
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException e) {
        return respond(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> badCredentials(BadCredentialsException e) {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHENTICATED, "Niepoprawny identyfikator lub haslo");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> authentication(AuthenticationException e) {
        return unauthenticated();
    }

    // --- 500 ---

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> internal(Exception e) {
        log.error("Nieobslugiwany wyjatek", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL, "Wystapil nieoczekiwany blad");
    }

    // --- pomocnicze ---

    private ResponseEntity<ProblemDetail> unauthenticated() {
        return respond(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHENTICATED, "Wymagane uwierzytelnienie");
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, ApiErrorCode code, String detail) {
        return ResponseEntity.status(status).body(ApiProblem.of(status, code, detail));
    }

    private static ResponseEntity<ProblemDetail> validation(List<FieldError> errors) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiProblem.validation("Walidacja nie powiodla sie", errors));
    }

    private ResponseEntity<Object> validationEntity(Exception ex, List<FieldError> errors, HttpHeaders headers,
            WebRequest request) {
        ProblemDetail pd = ApiProblem.validation("Walidacja nie powiodla sie", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    private static String sqlState(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static <T extends Throwable> Throwable findCause(Throwable t, Class<T> type) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (type.isInstance(c)) {
                return c;
            }
        }
        return null;
    }

    private static String path(JacksonException je) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : je.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String lastNode(Path path) {
        String last = null;
        for (Path.Node node : path) {
            last = node.getName();
        }
        return last;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String allowedValues(Class<?> enumType) {
        return WireEnums.allowed((Class) enumType);
    }
}
