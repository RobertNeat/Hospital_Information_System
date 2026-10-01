package robert_neat.his_backend.common.fhir;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.web.bind.annotation.RestController;

/** Kontroler FHIR: bledy wracaja jako `OperationOutcome` ({@link FhirExceptionHandler}). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
public @interface FhirEndpoint {
}
