package robert_neat.his_backend.common.wire;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Konwersja parametrow query / zmiennych sciezki `String` -> {@link WireEnum}
 * (np. `status=specimen_collected`, `bloodType=0+`, `reimbursement=100%`).
 * Nieznana wartosc daje {@link IllegalArgumentException}, mapowany przez advice na 422 VALIDATION_FAILED.
 * Pusty lancuch = brak wartosci (null), jak w standardowej konwersji enumow Springa.
 */
public class WireEnumConverterFactory implements ConverterFactory<String, WireEnum> {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends WireEnum> Converter<String, T> getConverter(Class<T> targetType) {
        if (!targetType.isEnum()) {
            throw new IllegalArgumentException("WireEnum musi byc enumem: " + targetType.getName());
        }
        Class enumType = targetType;
        return source -> source.isBlank() ? null : (T) WireEnums.fromWire(enumType, source.trim());
    }
}
