package robert_neat.his_backend.common.order;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `OrderUrgency` z kontraktu (rutynowe, pilne, natychmiastowe/CITO); wspolne dla lab i obrazowania. */
public enum Urgency implements WireEnum {
    ROUTINE("routine"),
    URGENT("urgent"),
    STAT("stat");

    private final String wire;

    Urgency(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<Urgency> {

        public JpaConverter() {
            super(Urgency.class);
        }
    }
}
