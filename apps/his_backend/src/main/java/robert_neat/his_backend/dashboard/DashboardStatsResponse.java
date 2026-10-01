package robert_neat.his_backend.dashboard;

/**
 * `DashboardStats` z kontraktu. `criticalAlerts` i `openTasks` (`@viewerScoped`) dotycza zalogowanego uzytkownika.
 */
public record DashboardStatsResponse(
        long admittedPatients,
        long newResults,
        long criticalAlerts,
        long openTasks,
        long pendingOrders,
        long vitalsAnomalies) {
}
