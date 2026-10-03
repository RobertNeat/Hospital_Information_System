package robert_neat.his_backend.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.DispatcherType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bezstanowe zabezpieczenie API: JWT (HS256) w naglowku `Authorization: Bearer`, bez sesji, CSRF i (domyslnie) CORS.
 * Klasa nie zalezy od JPA, wiec dziala w {@code @WebMvcTest} z {@code @Import(SecurityConfig.class)}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, LockoutProperties.class, CorsProperties.class})
public class SecurityConfig {

    static final int MIN_SECRET_BYTES = 32;
    /** Lista oddzialow dla formularza rejestracji (przed zalogowaniem); tylko GET. */
    private static final String PUBLIC_WARDS_PATH = "/api/v1/auth/register/wards";
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register",
            PUBLIC_WARDS_PATH);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            ProblemSecurityHandlers problems, CorsProperties cors, TokenVersionLookup tokenVersions) throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        if (cors.allowedOrigins().isEmpty()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else {
            http.cors(c -> c.configurationSource(corsSource(cors)));
        }
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_WARDS_PATH).permitAll()
                // Swagger UI/OpenAPI: tresc jest tylko odbiciem istniejacego, jawnie udokumentowanego REST API
                // (/api/**, scope: springdoc.paths-to-match) - LAN-wewnetrzny system, ten sam poziom ryzyka jak
                // juz publiczny /actuator/health/**; bez logowania, zgodnie z uzyciem przez personel i narzedzia
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                .permitAll()
                // handshake WebSocket bez Authorization (przegladarka nie doda naglowka): uwierzytelnienie jest
                // w ramce STOMP CONNECT (StompSecurityInterceptor); CONNECT bez waznego JWT jest odrzucany,
                // a po samym handshake nie da sie nic subskrybowac ani wyslac
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        http.exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems));
        http.oauth2ResourceServer(o -> o
                .bearerTokenResolver(publicPathsIgnoringResolver())
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems)
                .jwt(j -> j.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new HisJwtAuthenticationConverter(tokenVersions))));
        return http.build();
    }

    /** Przeterminowany/bledny naglowek Bearer nie moze blokowac publicznych endpointow (login, rejestracja, handshake `/ws`). */
    private static BearerTokenResolver publicPathsIgnoringResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> PUBLIC_AUTH_PATHS.contains(request.getRequestURI()) || isWebSocketHandshake(request) ? null
                : delegate.resolve(request);
    }

    private static boolean isWebSocketHandshake(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/ws") || path.startsWith("/ws/");
    }

    private static CorsConfigurationSource corsSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    ProblemSecurityHandlers problemSecurityHandlers(JsonMapper mapper) {
        return new ProblemSecurityHandlers(mapper);
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.secret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Brak lub za krotki klucz JWT: ustaw his.security.jwt.secret "
                    + "(zmienna srodowiskowa HIS_JWT_SECRET) o dlugosci co najmniej " + MIN_SECRET_BYTES
                    + " bajtow");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    JwtTokenService jwtTokenService(JwtEncoder encoder, JwtProperties properties) {
        return new JwtTokenService(encoder, properties);
    }

    /** BCrypt cost 10; hashe w bazie sa "gole" (`$2a$10$...`), bez prefiksu `{bcrypt}`. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
