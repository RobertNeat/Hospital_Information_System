package robert_neat.his_backend.catalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Katalog laboratoryjny (`GET /lab-tests`, `GET /lab-panels`): dane referencyjne, tylko odczyt. */
@Service
@Transactional(readOnly = true)
public class LabCatalogService {

    private final LabTestRepository tests;
    private final LabPanelRepository panels;

    LabCatalogService(LabTestRepository tests, LabPanelRepository panels) {
        this.tests = tests;
        this.panels = panels;
    }

    /** Wszystkie badania z analitami, alfabetycznie wg nazwy (kolacja polska). */
    public List<LabTestResponse> tests() {
        return tests.findAll().stream()
                .sorted(CatalogMapper.<LabTest>byText(LabTest::getName).thenComparing(LabTest::getCode))
                .map(CatalogMapper::toResponse).toList();
    }

    /** Wszystkie panele, alfabetycznie wg nazwy. */
    public List<LabPanelResponse> panels() {
        return panels.findAll().stream()
                .sorted(CatalogMapper.<LabPanel>byText(LabPanel::getName).thenComparing(LabPanel::getId))
                .map(CatalogMapper::toResponse).toList();
    }
}
