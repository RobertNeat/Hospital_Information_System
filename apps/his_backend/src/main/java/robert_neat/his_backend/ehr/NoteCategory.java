package robert_neat.his_backend.ehr;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `NoteCategory` z kontraktu. */
public enum NoteCategory implements WireEnum {
    ADMISSION("admission"),
    PROGRESS("progress"),
    CONSULTATION("consultation"),
    NURSING("nursing"),
    OBSERVATION("observation"),
    DISCHARGE("discharge");

    private final String wire;

    NoteCategory(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<NoteCategory> {

        public JpaConverter() {
            super(NoteCategory.class);
        }
    }
}
