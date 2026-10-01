package robert_neat.his_backend.ehr;

import java.util.List;
import java.util.UUID;

/**
 * Punkt rozszerzenia dla EhrSummary.activeMedications. Modul ehr zbiera wyniki ze WSZYSTKICH beanow tego typu
 * (kolejnosc wg {@code @Order}), wiec modul recept (K14) wystarczy, ze zarejestruje wlasny bean - bez zmian w ehr i
 * bez zaleznosci cyklicznej (recepty zaleza od ehr, nie odwrotnie). Implementacja dla recept: {@code prescription.PrescriptionActiveMedicationsProvider}.
 */
public interface ActiveMedicationsProvider {

    List<? extends PrescriptionItemView> activeMedications(UUID patientId);
}
