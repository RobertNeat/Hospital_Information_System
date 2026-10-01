package robert_neat.ereceipt.mtls;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * TEST-ONLY: tymczasowe PKI generowane przez {@code keytool} z biezacego JDK (bez dodatkowych bibliotek i bez
 * plikow w repo). Hasla i klucze sa jednorazowe i zyja tylko w katalogu tymczasowym testu.
 */
public final class TestPki {

    public static final String PASSWORD = "test-only-changeit";

    private static final String SAN = "dns:localhost,ip:127.0.0.1";
    private static final Object LOCK = new Object();
    private static TestPki shared;

    private final Path dir;
    private final Path caStore;
    private final Path caCert;
    private final Path trustStore;

    private TestPki(Path dir, String caName) throws IOException {
        this.dir = dir;
        this.caStore = dir.resolve(caName + ".p12");
        this.caCert = dir.resolve(caName + ".crt");
        this.trustStore = dir.resolve(caName + "-truststore.p12");
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=TEST-ONLY " + caName,
                "-ext", "bc:c", "-validity", "30", "-keystore", caStore.toString());
        keytool("-exportcert", "-alias", "ca", "-rfc", "-file", caCert.toString(), "-keystore", caStore.toString());
        keytool("-importcert", "-alias", "ca", "-file", caCert.toString(), "-noprompt", "-keystore",
                trustStore.toString());
    }

    /** Wspolne PKI na caly przebieg JVM (kilka wywolan keytool trwa sekundy). */
    public static TestPki shared() {
        synchronized (LOCK) {
            if (shared == null) {
                shared = create("test-ca");
            }
            return shared;
        }
    }

    /** Nowe, niezalezne CA (np. obcy wystawca certyfikatu klienta). */
    public static TestPki create(String caName) {
        try {
            Path dir = Files.createTempDirectory("his-test-pki-");
            dir.toFile().deleteOnExit();
            return new TestPki(dir, caName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Keystore PKCS12 z certyfikatem o danym CN (SAN localhost, EKU serverAuth+clientAuth) podpisanym tym CA. */
    public Path issue(String cn) {
        try {
            Path store = dir.resolve(cn + "-" + System.nanoTime() + ".p12");
            Path csr = dir.resolve(cn + ".csr");
            Path crt = dir.resolve(cn + ".crt");
            Files.deleteIfExists(csr);
            Files.deleteIfExists(crt);
            keytool("-genkeypair", "-alias", "entity", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=" + cn,
                    "-ext", "SAN=" + SAN, "-validity", "30", "-keystore", store.toString());
            keytool("-certreq", "-alias", "entity", "-file", csr.toString(), "-ext", "SAN=" + SAN,
                    "-keystore", store.toString());
            keytool("-gencert", "-alias", "ca", "-infile", csr.toString(), "-outfile", crt.toString(), "-rfc",
                    "-ext", "SAN=" + SAN, "-ext", "eku=serverAuth,clientAuth", "-validity", "30", "-keystore",
                    caStore.toString());
            keytool("-importcert", "-alias", "ca", "-file", caCert.toString(), "-noprompt", "-keystore",
                    store.toString());
            keytool("-importcert", "-alias", "entity", "-file", crt.toString(), "-keystore", store.toString());
            return store;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path trustStore() {
        return trustStore;
    }

    /** Certyfikat (z klucza publicznego) o danym CN, do {@code SecurityMockMvcRequestPostProcessors.x509}. */
    public X509Certificate certificate(String cn) {
        try {
            KeyStore ks = load(issue(cn));
            return (X509Certificate) ks.getCertificate("entity");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Kontekst TLS klienta: opcjonalny certyfikat klienta ({@code clientStore}) + zaufanie do CA z {@code trusted}. */
    public static SSLContext sslContext(Path clientStore, TestPki trusted) {
        try {
            KeyManagerFactory kmf = null;
            if (clientStore != null) {
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(load(clientStore), PASSWORD.toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(load(trusted.trustStore));
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyStore load(Path path) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            ks.load(in, PASSWORD.toCharArray());
        }
        return ks;
    }

    private static void keytool(String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(List.of(args));
        cmd.addAll(List.of("-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD));
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool " + args[0] + " zakonczyl sie bledem: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
