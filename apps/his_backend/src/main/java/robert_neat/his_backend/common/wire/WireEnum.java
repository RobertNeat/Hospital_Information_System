package robert_neat.his_backend.common.wire;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum, ktorego wartosc na drucie (JSON, parametry query, kolumna w bazie) rozni sie od nazwy stalej Javy,
 * np. `PERCENT_100` <-> `"100%"`, `BLOOD_0_PLUS` <-> `"0+"`, `ICD_10` <-> `"ICD-10"`.
 * <p>
 * Wzorzec: enum implementuje `WireEnum` i zwraca wartosc z unii TS w {@link #wire()}.
 * `@JsonValue` na metodzie interfejsu dziala dla serializacji i deserializacji kazdej implementacji
 * (Jackson dziedziczy adnotacje z interfejsow), wiec enumy nie potrzebuja wlasnego `@JsonCreator`.
 * Persystencja: podklasa {@link WireEnumConverter}; parametry query: {@link WireEnumConverterFactory}.
 */
public interface WireEnum {

    @JsonValue
    String wire();
}
