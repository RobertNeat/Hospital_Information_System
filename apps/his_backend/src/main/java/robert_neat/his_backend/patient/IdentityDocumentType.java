package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `IdentityDocumentType` z kontraktu. */
public enum IdentityDocumentType implements WireEnum {
    ID_CARD("id_card"),
    PASSPORT("passport"),
    OTHER("other");

    private final String wire;

    IdentityDocumentType(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<IdentityDocumentType> {

        public JpaConverter() {
            super(IdentityDocumentType.class);
        }
    }
}
