package robert_neat.his_backend.vitals;

import java.util.List;

/** `VitalsRecordResponse` z kontraktu: zapisany odczyt i anomalie wyliczone przez serwer. */
public record VitalsRecordResponse(VitalSignsResponse saved, List<VitalAnomaly> anomalies) {
}
