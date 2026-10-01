package robert_neat.his_backend.common.wire;

/** Enumy testowe odtwarzajace "trudne" wartosci z kontraktu (nie sa poprawnymi identyfikatorami Javy). */
public final class WireSamples {

    private WireSamples() {
    }

    public enum Reimbursement implements WireEnum {
        PERCENT_100("100%"), PERCENT_50("50%"), PERCENT_30("30%");

        private final String wire;

        Reimbursement(String wire) {
            this.wire = wire;
        }

        @Override
        public String wire() {
            return wire;
        }
    }

    public enum Blood implements WireEnum {
        A_PLUS("A+"), AB_MINUS("AB-"), BLOOD_0_PLUS("0+"), BLOOD_0_MINUS("0-");

        private final String wire;

        Blood(String wire) {
            this.wire = wire;
        }

        @Override
        public String wire() {
            return wire;
        }
    }

    public enum Coding implements WireEnum {
        ICD_10("ICD-10"), ICD_9_PL("ICD-9-PL"), LOCAL("local");

        private final String wire;

        Coding(String wire) {
            this.wire = wire;
        }

        @Override
        public String wire() {
            return wire;
        }
    }

    public enum Modality implements WireEnum {
        USG, RTG, CT;

        @Override
        public String wire() {
            return name();
        }
    }

    public enum OrderStatus implements WireEnum {
        ORDERED("ordered"), SPECIMEN_COLLECTED("specimen_collected");

        private final String wire;

        OrderStatus(String wire) {
            this.wire = wire;
        }

        @Override
        public String wire() {
            return wire;
        }
    }

    public record Payload(Reimbursement reimbursement, Blood blood, Coding coding, Modality modality,
            OrderStatus status) {
    }

    @jakarta.persistence.Converter
    public static class BloodConverter extends WireEnumConverter<Blood> {
        public BloodConverter() {
            super(Blood.class);
        }
    }
}
