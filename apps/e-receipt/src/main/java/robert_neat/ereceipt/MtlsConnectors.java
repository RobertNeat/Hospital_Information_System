package robert_neat.ereceipt;

import java.io.IOException;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rozdzielenie ruchu przy aktywnym mTLS (domyslnie; wylacza go HIS_MTLS_ENABLED=false): port aplikacji to HTTPS z
 * wymaganym certyfikatem klienta i obsluguje wylacznie `/fhir/**`; dodatkowy konektor HTTP (`mtls.http-port`) sluzy
 * przegladarce i obsluguje wylacznie UI (`/`, `/ui/**`), wiec FHIR nie jest tam dostepny bez certyfikatu.
 * Rozroznienie po {@code request.isSecure()} (nie po numerze portu); naglowkow X-Forwarded-* nie respektujemy.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("mtls.http-port")
class MtlsConnectors {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> plainHttpConnector(
            @Value("${mtls.http-port}") int port) {
        return factory -> {
            Connector connector = new Connector("HTTP/1.1");
            connector.setPort(port);
            connector.setScheme("http");
            connector.setSecure(false);
            factory.addAdditionalConnectors(connector);
        };
    }

    @Bean
    FilterRegistrationBean<ConnectorGuardFilter> connectorGuard() {
        FilterRegistrationBean<ConnectorGuardFilter> registration = new FilterRegistrationBean<>(
                new ConnectorGuardFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** HTTPS: tylko `/fhir/**`; HTTP: tylko UI (`/`, `/ui/**`); inaczej 404. */
    static final class ConnectorGuardFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI();
            boolean allowed = request.isSecure() ? isUnder(path, "/fhir")
                    : path.equals("/") || isUnder(path, "/ui");
            if (allowed) {
                chain.doFilter(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }

        private static boolean isUnder(String path, String prefix) {
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
    }
}
