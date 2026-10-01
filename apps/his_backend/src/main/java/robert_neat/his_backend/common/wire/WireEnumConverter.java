package robert_neat.his_backend.common.wire;

import jakarta.persistence.AttributeConverter;

/**
 * Bazowy konwerter JPA: enum <-> wartosc na drucie (kolumna `varchar` + `CHECK`, nigdy ORDINAL).
 * Uzycie:
 * <pre>
 * {@code @Converter(autoApply = true)}
 * public class StaffRoleConverter extends WireEnumConverter&lt;StaffRole&gt; {
 *     public StaffRoleConverter() { super(StaffRole.class); }
 * }
 * </pre>
 */
public abstract class WireEnumConverter<E extends Enum<E> & WireEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected WireEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : WireEnums.fromWire(type, dbData);
    }
}
