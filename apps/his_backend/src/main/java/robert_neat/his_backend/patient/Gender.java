package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `Gender` z kontraktu (kody FHIR). */
public enum Gender implements WireEnum {
    FEMALE("female"),
    MALE("male"),
    OTHER("other"),
    UNKNOWN("unknown");

    private final String wire;

    Gender(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<Gender> {

        public JpaConverter() {
            super(Gender.class);
        }
    }
}
