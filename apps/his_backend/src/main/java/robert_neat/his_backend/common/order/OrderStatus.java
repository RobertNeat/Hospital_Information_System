package robert_neat.his_backend.common.order;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/**
 * `OrderStatus` z kontraktu, wspolny dla zlecen laboratoryjnych i obrazowych (`specimen_collected` dotyczy tylko
 * laboratorium). Dozwolone przejscia zalezne od typu zlecenia sa w module danego zlecenia.
 */
public enum OrderStatus implements WireEnum {
    ORDERED("ordered"),
    SCHEDULED("scheduled"),
    SPECIMEN_COLLECTED("specimen_collected"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String wire;

    OrderStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    /** `completed` i `cancelled` - ze stanu koncowego nie ma przejsc. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<OrderStatus> {

        public JpaConverter() {
            super(OrderStatus.class);
        }
    }
}
