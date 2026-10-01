package robert_neat.his_backend.common.fhir;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

import ca.uhn.fhir.context.FhirContext;
import jakarta.servlet.DispatcherType;

/**
 * Osobny lancuch dla `/fhir/**` (wywolania uslug e-*), przed lancuchem JWT. Uwierzytelnienie = certyfikat klienta
 * z mTLS (Tomcat weryfikuje lancuch wzgledem truststore), a tu sprawdzany jest CN z listy dozwolonych. Bez certyfikatu
 * (np. tryb `nomtls`) kazde zadanie to 401 z `OperationOutcome`.
 */
@Configuration(proxyBeanMethods = false)
class FhirSecurityConfig {

    static final String ROLE_SERVICE = "ROLE_FHIR_SERVICE";
    private static final String CN_REGEX = "CN=(.*?)(?:,|$)";

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
                .x509(x -> x.subjectPrincipalRegex(CN_REGEX).userDetailsService(allowedClients(properties)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasAuthority(ROLE_SERVICE))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorized(fhir))
                        .accessDeniedHandler((req, res, ex) -> unauthorized(fhir).commence(req, res, null)));
        return http.build();
    }

    /** Dozwolone sa wylacznie CN z konfiguracji; kazdy inny (nawet z zaufanego CA) nie dostaje roli uslugowej. */
    private static UserDetailsService allowedClients(FhirServiceProperties properties) {
        return cn -> {
            if (!properties.allowedClientCns().contains(cn)) {
                throw new UsernameNotFoundException("CN klienta niedozwolony");
            }
            return User.withUsername(cn).password("").authorities(List.of(new SimpleGrantedAuthority(ROLE_SERVICE)))
                    .build();
        };
    }

    private static AuthenticationEntryPoint unauthorized(FhirContext fhir) {
        return (request, response, ex) -> {
            OperationOutcome oo = new OperationOutcome();
            oo.addIssue().setSeverity(IssueSeverity.ERROR).setCode(IssueType.SECURITY)
                    .setDiagnostics("Wymagany certyfikat klienta (mTLS) z dozwolonym CN");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(FhirSystems.FHIR_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(fhir.newJsonParser().encodeResourceToString(oo));
        };
    }
}
