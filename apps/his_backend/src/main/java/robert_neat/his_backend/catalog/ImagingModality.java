package robert_neat.his_backend.catalog;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `ImagingModality` z kontraktu (wartosci wielkimi literami takze na drucie). */
public enum ImagingModality implements WireEnum {
    USG("USG"),
    RTG("RTG"),
    CT("CT"),
    MRI("MRI"),
    MMG("MMG"),
    ENDOSCOPY("ENDOSCOPY"),
    COLONOSCOPY("COLONOSCOPY"),
    ANGIOGRAPHY("ANGIOGRAPHY");

    private final String wire;

    ImagingModality(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<ImagingModality> {

        public JpaConverter() {
            super(ImagingModality.class);
        }
    }
}
