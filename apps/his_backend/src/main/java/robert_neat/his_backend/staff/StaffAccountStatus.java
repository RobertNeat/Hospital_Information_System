package robert_neat.his_backend.staff;

import robert_neat.his_backend.common.wire.WireEnum;

/** `StaffAccountStatus` z kontraktu (`user_account.account_status`). */
public enum StaffAccountStatus implements WireEnum {
    PENDING("pending"),
    ACTIVE("active"),
    LOCKED("locked");

    private final String wire;

    StaffAccountStatus(String wire) {
        this.wire = wire;
    }

    @Override
    public String wire() {
        return wire;
    }
}
