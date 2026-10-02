package robert_neat.ereceipt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Symulator bez uwierzytelniania aplikacyjnego: publiczne sa probes zdrowia, UI Thymeleaf i FHIR (`/fhir/**`);
 * reszta zablokowana. `/fhir/**` zabezpiecza mTLS na porcie aplikacji (profil `mtls`, domyslny); UI (bez uwierzytelniania) jest na
 * osobnym porcie HTTP (zob. {@code MtlsConnectors}).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/", "/ui/**", "/css/**", "/js/**", "/images/**", "/fhir/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
