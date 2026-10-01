package robert_neat.his_backend.terminology.snomed;

import java.util.Locale;

/** Rodzaj podpowiadanej terminologii (parametr `kind`). */
public enum TerminologyKind {
    DIAGNOSIS, SYMPTOM, PROCEDURE;

    public static TerminologyKind parse(String raw) {
        if (raw != null) {
            for (TerminologyKind k : values()) {
                if (k.name().equals(raw.strip().toUpperCase(Locale.ROOT))) {
                    return k;
                }
            }
        }
        throw new TerminologyException.InvalidRequest("kind musi byc jednym z: diagnosis, symptom, procedure", null);
    }
}
