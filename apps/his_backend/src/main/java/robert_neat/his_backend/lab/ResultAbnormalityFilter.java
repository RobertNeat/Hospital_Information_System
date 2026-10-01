package robert_neat.his_backend.lab;

import robert_neat.his_backend.common.wire.WireEnum;

/** `ResultAbnormalityFilter` z kontraktu (parametr `filter`): `abnormal` = jakakolwiek flaga != N, `critical` = LL/HH. */
public enum ResultAbnormalityFilter implements WireEnum {
    ALL("all"),
    ABNORMAL("abnormal"),
    CRITICAL("critical");

    private final String wire;

    ResultAbnormalityFilter(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
