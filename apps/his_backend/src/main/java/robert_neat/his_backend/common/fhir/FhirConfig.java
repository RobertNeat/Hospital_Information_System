package robert_neat.his_backend.common.fhir;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FhirServiceProperties.class)
class FhirConfig {

    /** `FhirContext` jest kosztowny przy tworzeniu i bezpieczny watkowo: jeden egzemplarz R4. */
    @Bean
    FhirContext fhirContext() {
        return FhirContext.forR4Cached();
    }
}
