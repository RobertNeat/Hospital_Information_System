package robert_neat.elaboratory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * Symulator bez uwierzytelniania aplikacyjnego: publiczne sa probes zdrowia, UI Thymeleaf i FHIR (`/fhir/**`);
 * reszta zablokowana. `/fhir/**` zabezpiecza mTLS na porcie aplikacji (profil `mtls`, domyslny); UI (bez uwierzytelniania) jest na
 * osobnym porcie HTTP (zob. {@code MtlsConnectors}).
 * CSRF: wlaczone dla formularzy UI (token w cookie, bo sesje sa STATELESS); wylaczone dla `/fhir/**`, bo to API
 * maszynowe wywolywane przez HIS przez mTLS, nie przez przegladarke z cookies.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Token zaladowany eagerly (nie leniwie): przy leniwym ladowaniu token (i cookie XSRF-TOKEN) powstaje
        // dopiero gdy cos faktycznie po niego sięgnie - przy strumieniowanym renderowaniu Thymeleaf moze to
        // nastapic po wyslaniu naglowkow odpowiedzi, co bezpowrotnie gubi Set-Cookie. XorCsrfTokenRequestAttributeHandler
        // (nie plain CsrfTokenRequestAttributeHandler) zachowuje maskowanie BREACH przy generowanym tokenie.
        XorCsrfTokenRequestAttributeHandler csrfHandler = new XorCsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(new CookieCsrfTokenRepository())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/fhir/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/", "/ui/**", "/css/**", "/js/**", "/images/**", "/fhir/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
