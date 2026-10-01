package robert_neat.his_backend.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import robert_neat.his_backend.common.api.ApiErrorCode;
import robert_neat.his_backend.common.api.ApiProblem;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401/403 z warstwy filtrow (przed MVC) jako `application/problem+json` w ksztalcie {@link ApiProblem}.
 * Tresc jest celowo ogolna (bez przyczyny bledu tokenu).
 */
final class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    ProblemSecurityHandlers(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHENTICATED, "Wymagane uwierzytelnienie");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Brak uprawnien do wykonania operacji");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            ApiErrorCode code, String detail) throws IOException {
        ProblemDetail pd = ApiProblem.of(status, code, detail);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", pd.getType().toString());
        body.put("title", pd.getTitle());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put(ApiProblem.PROPERTY_CODE, code.name());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
