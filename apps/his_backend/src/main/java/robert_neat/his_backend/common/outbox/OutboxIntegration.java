package robert_neat.his_backend.common.outbox;

import jakarta.persistence.Converter;

import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** Docelowa integracja FHIR dla wiersza outboxa (kolumna `integration_outbox.integration`). */
public enum OutboxIntegration implements WireEnum {
    ERECEIPT("ereceipt"),
    ELAB("elab"),
    EIMG("eimg");

    private final String wire;

    OutboxIntegration(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<OutboxIntegration> {
        public JpaConverter() {
            super(OutboxIntegration.class);
        }
    }
}
