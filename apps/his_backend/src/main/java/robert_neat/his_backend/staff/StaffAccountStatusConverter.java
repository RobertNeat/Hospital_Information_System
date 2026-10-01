package robert_neat.his_backend.staff;

import jakarta.persistence.Converter;
import robert_neat.his_backend.common.wire.WireEnumConverter;

@Converter(autoApply = true)
public class StaffAccountStatusConverter extends WireEnumConverter<StaffAccountStatus> {

    public StaffAccountStatusConverter() {
        super(StaffAccountStatus.class);
    }
}
