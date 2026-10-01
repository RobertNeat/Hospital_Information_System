package robert_neat.his_backend.common.persistence;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import robert_neat.his_backend.common.security.CurrentActor;

/** Osobna konfiguracja (nie na klasie aplikacji), zeby nie psula testow slice (@WebMvcTest). */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class JpaAuditingConfig {

    @Bean
    AuditorAware<UUID> auditorAware(CurrentActor currentActor) {
        return currentActor::staffId;
    }
}
