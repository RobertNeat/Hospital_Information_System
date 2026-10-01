package robert_neat.his_backend.common.fhir;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.x509;

import java.security.cert.X509Certificate;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Uwierzytelnienie wywolan `/fhir/**` w MockMvc certyfikatem klienta (TEST-ONLY PKI z {@link TestPki}). */
public final class FhirTestAuth {

    private static X509Certificate service;
    private static X509Certificate intruder;

    private FhirTestAuth() {
    }

    /** Certyfikat zaufanego CA z dozwolonym CN (`e-receipt`). */
    public static synchronized RequestPostProcessor service() {
        if (service == null) {
            service = TestPki.shared().certificate("e-receipt");
        }
        return x509(service);
    }

    /** Certyfikat zaufanego CA, ale z CN spoza listy dozwolonych. */
    public static synchronized RequestPostProcessor intruder() {
        if (intruder == null) {
            intruder = TestPki.shared().certificate("intruder");
        }
        return x509(intruder);
    }
}
