package robert_neat.his_backend.dashboard;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.alert.AlertService;
import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.order.OrderStatus;
import robert_neat.his_backend.common.security.CurrentActor;
import robert_neat.his_backend.imaging.ImagingOrder;
import robert_neat.his_backend.imaging.ImagingOrderRepository;
import robert_neat.his_backend.lab.LabOrder;
import robert_neat.his_backend.lab.LabOrderRepository;
import robert_neat.his_backend.lab.LabResult;
import robert_neat.his_backend.lab.LabResultRepository;
import robert_neat.his_backend.messaging.TaskStatus;
import robert_neat.his_backend.messaging.TeamTask;
import robert_neat.his_backend.messaging.TeamTaskRepository;
import robert_neat.his_backend.patient.Patient;
import robert_neat.his_backend.patient.PatientRepository;
import robert_neat.his_backend.patient.PatientStatus;
import robert_neat.his_backend.vitals.VitalsService;
import robert_neat.his_backend.vitals.WardVitalsRow;

/**
 * Liczniki dashboardu liczone w bazie (backend jest autorytatywny). Definicje:
 * <ul>
 *   <li>`admittedPatients` - pacjenci ze statusem `admitted`;</li>
 *   <li>`newResults` - wyniki laboratoryjne jeszcze niepotwierdzone przez lekarza (`reviewedAt` puste);</li>
 *   <li>`criticalAlerts` - alerty `critical` niepotwierdzone przez biezacego uzytkownika;</li>
 *   <li>`openTasks` - zadania `open` przypisane do biezacego uzytkownika;</li>
 *   <li>`pendingOrders` - zlecenia laboratoryjne i obrazowe w toku (`ordered`, `scheduled`, `specimen_collected`,
 *       `in_progress`);</li>
 *   <li>`vitalsAnomalies` - pacjenci z przegladu parametrow zyciowych (wszystkie oddzialy) z co najmniej jedna
 *       anomalia ({@link VitalsService#wardOverview}).</li>
 * </ul>
 * Zaleznosci: dashboard zalezy od modulow domenowych, nigdy odwrotnie.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<OrderStatus> IN_FLIGHT = List.of(OrderStatus.ORDERED, OrderStatus.SCHEDULED,
            OrderStatus.SPECIMEN_COLLECTED, OrderStatus.IN_PROGRESS);

    private final PatientRepository patients;
    private final LabResultRepository labResults;
    private final LabOrderRepository labOrders;
    private final ImagingOrderRepository imagingOrders;
    private final TeamTaskRepository tasks;
    private final AlertService alerts;
    private final VitalsService vitals;
    private final CurrentActor currentActor;

    DashboardService(PatientRepository patients, LabResultRepository labResults, LabOrderRepository labOrders,
            ImagingOrderRepository imagingOrders, TeamTaskRepository tasks, AlertService alerts,
            VitalsService vitals, CurrentActor currentActor) {
        this.patients = patients;
        this.labResults = labResults;
        this.labOrders = labOrders;
        this.imagingOrders = imagingOrders;
        this.tasks = tasks;
        this.alerts = alerts;
        this.vitals = vitals;
        this.currentActor = currentActor;
    }

    /** 403 brak powiazania sesji z pracownikiem (liczniki per uzytkownik). */
    public DashboardStatsResponse stats() {
        UUID me = currentActor.staffId()
                .orElseThrow(() -> new ForbiddenException("Brak powiazania sesji z pracownikiem"));
        long admitted = patients.count(
                (Specification<Patient>) (root, q, cb) -> cb.equal(root.get("status"), PatientStatus.ADMITTED));
        long newResults = labResults
                .count((Specification<LabResult>) (root, q, cb) -> cb.isNull(root.get("reviewedAt")));
        long openTasks = tasks.count((Specification<TeamTask>) (root, q, cb) -> cb
                .and(cb.equal(root.get("assignedToId"), me), cb.equal(root.get("status"), TaskStatus.OPEN)));
        long pending = labOrders.count((Specification<LabOrder>) (root, q, cb) -> root.get("status").in(IN_FLIGHT))
                + imagingOrders
                        .count((Specification<ImagingOrder>) (root, q, cb) -> root.get("status").in(IN_FLIGHT));
        long anomalies = vitals.wardOverview(null).stream().filter((WardVitalsRow r) -> !r.anomalies().isEmpty())
                .count();
        return new DashboardStatsResponse(admitted, newResults, alerts.countUnacknowledgedCritical(me), openTasks,
                pending, anomalies);
    }
}
