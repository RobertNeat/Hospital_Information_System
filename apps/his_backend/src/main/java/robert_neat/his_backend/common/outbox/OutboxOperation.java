package robert_neat.his_backend.common.outbox;

import jakarta.persistence.Converter;

import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/**
 * Rodzaj wysylki do ponowienia: `SUBMIT` to poczatkowy `POST` zlecenia/recepty, `CANCEL` to `PUT` anulowania
 * zainicjowanego w HIS. Rozne operacje na tej samej encji maja osobne wiersze (`uq_integration_outbox_entry`).
 */
public enum OutboxOperation implements WireEnum {
    SUBMIT("submit"),
    CANCEL("cancel");

    private final String wire;

    OutboxOperation(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<OutboxOperation> {
        public JpaConverter() {
            super(OutboxOperation.class);
        }
    }
}
