package robert_neat.his_backend.patient;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnum;
import robert_neat.his_backend.common.wire.WireEnumConverter;

/** `EpisodeStatus` z kontraktu. */
public enum EpisodeStatus implements WireEnum {
    ACTIVE("active"),
    CLOSED("closed");

    private final String wire;

    EpisodeStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }

    @Converter(autoApply = true)
    public static class JpaConverter extends WireEnumConverter<EpisodeStatus> {

        public JpaConverter() {
            super(EpisodeStatus.class);
        }
    }
}
