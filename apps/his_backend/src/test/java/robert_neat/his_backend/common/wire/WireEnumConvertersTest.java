package robert_neat.his_backend.common.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.support.DefaultConversionService;

import robert_neat.his_backend.common.wire.WireSamples.Blood;
import robert_neat.his_backend.common.wire.WireSamples.BloodConverter;
import robert_neat.his_backend.common.wire.WireSamples.Modality;
import robert_neat.his_backend.common.wire.WireSamples.OrderStatus;
import robert_neat.his_backend.common.wire.WireSamples.Reimbursement;

class WireEnumConvertersTest {

    private final DefaultConversionService conversion = new DefaultConversionService();

    WireEnumConvertersTest() {
        conversion.addConverterFactory(new WireEnumConverterFactory());
    }

    @Test
    void queryParamsConvertFromWireValues() {
        assertThat(conversion.convert("specimen_collected", OrderStatus.class)).isEqualTo(OrderStatus.SPECIMEN_COLLECTED);
        assertThat(conversion.convert("0+", Blood.class)).isEqualTo(Blood.BLOOD_0_PLUS);
        assertThat(conversion.convert("100%", Reimbursement.class)).isEqualTo(Reimbursement.PERCENT_100);
        assertThat(conversion.convert("USG", Modality.class)).isEqualTo(Modality.USG);
    }

    @Test
    void blankIsNull() {
        assertThat(conversion.convert("", Blood.class)).isNull();
    }

    @Test
    void javaConstantNameIsRejected() {
        assertThatThrownBy(() -> conversion.convert("PERCENT_100", Reimbursement.class))
                .isInstanceOf(ConversionFailedException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("100%").hasMessageContaining("50%");
    }

    @Test
    void jpaConverterRoundTrips() {
        BloodConverter converter = new BloodConverter();
        assertThat(converter.convertToDatabaseColumn(Blood.BLOOD_0_MINUS)).isEqualTo("0-");
        assertThat(converter.convertToEntityAttribute("AB-")).isEqualTo(Blood.AB_MINUS);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("ZZ")).isInstanceOf(IllegalArgumentException.class);
    }
}
