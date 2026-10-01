package robert_neat.his_backend.common.fhir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

import ca.uhn.fhir.context.FhirContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Osobny lancuch dla `/fhir/**` (wywolania uslug e-*), przed lancuchem JWT: autoryzacja wspolnym kluczem uslugowym
 * z naglowka {@code X-Service-Key} (porownanie w stalym czasie; pusty klucz w konfiguracji = zawsze 401).
 * Odpowiedz 401 to `OperationOutcome`. Docelowo mTLS (profil `mtls`) zastapi klucz.
 */
@Configuration(proxyBeanMethods = false)
class FhirSecurityConfig {

    static final String ROLE_SERVICE = "ROLE_FHIR_SERVICE";

    @Bean
    @Order(1)
    SecurityFilterChain fhirSecurityFilterChain(HttpSecurity http, FhirServiceProperties properties,
            FhirContext fhir) throws Exception {
        http.securityMatcher("/fhir/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasAuthority(ROLE_SERVICE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorized(fhir))
                        .accessDeniedHandler((req, res, ex) -> unauthorized(fhir).commence(req, res, null)))
                .addFilterBefore(new ServiceKeyFilter(properties.serviceKey()), AuthorizationFilter.class);
        return http.build();
    }

    private static AuthenticationEntryPoint unauthorized(FhirContext fhir) {
        return (request, response, ex) -> {
            OperationOutcome oo = new OperationOutcome();
            oo.addIssue().setSeverity(IssueSeverity.ERROR).setCode(IssueType.SECURITY)
                    .setDiagnostics("Wymagany poprawny klucz uslugowy (" + FhirSystems.SERVICE_KEY_HEADER + ")");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(FhirSystems.FHIR_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(fhir.newJsonParser().encodeResourceToString(oo));
        };
    }

    /** Uwierzytelnia wywolanie uslugowe, gdy naglowek zawiera skonfigurowany klucz. */
    static final class ServiceKeyFilter extends OncePerRequestFilter {

        private final byte[] expected;

        ServiceKeyFilter(String serviceKey) {
            this.expected = serviceKey.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            String provided = request.getHeader(FhirSystems.SERVICE_KEY_HEADER);
            if (expected.length > 0 && provided != null
                    && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "fhir-service", null, List.of(new SimpleGrantedAuthority(ROLE_SERVICE))));
            }
            chain.doFilter(request, response);
        }
    }
}
