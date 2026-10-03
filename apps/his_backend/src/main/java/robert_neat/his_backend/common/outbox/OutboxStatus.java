package robert_neat.his_backend.common.outbox;

import jakarta.persistence.Converter;

import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** Stan wiersza outboxa: `PENDING` czeka na (pierwsze lub kolejne) ponowienie, `SUCCEEDED`/`FAILED` sa koncowe. */
public enum OutboxStatus implements WireEnum {
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed");

    private final String wire;

    OutboxStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<OutboxStatus> {
        public JpaConverter() {
            super(OutboxStatus.class);
        }
    }
}
