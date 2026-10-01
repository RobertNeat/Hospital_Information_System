package robert_neat.elaboratory.fhir;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;

@Configuration(proxyBeanMethods = false)
class FhirConfig {

    /** `FhirContext` jest kosztowny przy tworzeniu i bezpieczny watkowo: jeden egzemplarz R4. */
    @Bean
    FhirContext fhirContext() {
        return FhirContext.forR4Cached();
    }
}
