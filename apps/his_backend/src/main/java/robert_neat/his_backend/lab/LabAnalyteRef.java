package robert_neat.his_backend.lab;

/**
 * Odpowiedz `getTrendableAnalytes`: `{ code, name }`. TS nie ma tego typu (klient mapuje na `SelectOption`); nazwa wg
 * propozycji z API.md par. 15 (`LabAnalyteRef`).
 */
public record LabAnalyteRef(String code, String name) {
}
