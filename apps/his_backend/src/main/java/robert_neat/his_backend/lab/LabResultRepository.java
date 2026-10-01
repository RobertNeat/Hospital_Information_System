package robert_neat.his_backend.lab;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabResultRepository extends JpaRepository<LabResult, UUID>, JpaSpecificationExecutor<LabResult> {

    /** Punkty trendu (obserwacje liczbowe analitu pacjenta) od najstarszego wyniku wg daty pobrania. */
    @Query("""
            select new robert_neat.his_backend.lab.TrendRow(r.collectedAt, o.valueNumeric, o.flag, o.analyteName,
                   o.unit, o.referenceRange.low, o.referenceRange.high)
            from LabResult r join r.observations o
            where r.patientId = :patientId and o.analyteCode = :analyteCode and o.valueNumeric is not null
            order by r.collectedAt asc, r.id asc
            """)
    List<TrendRow> trendRows(@Param("patientId") UUID patientId, @Param("analyteCode") String analyteCode);

    /** Anality pacjenta z wartoscia liczbowa (nazwa = najpozniejsza leksykograficznie wsrod snapshotow tego kodu). */
    @Query("""
            select new robert_neat.his_backend.lab.LabAnalyteRef(o.analyteCode, max(o.analyteName))
            from LabResult r join r.observations o
            where r.patientId = :patientId and o.valueNumeric is not null
            group by o.analyteCode
            order by max(o.analyteName), o.analyteCode
            """)
    List<LabAnalyteRef> trendableAnalytes(@Param("patientId") UUID patientId);

    /** Pozycje zlecenia, dla ktorych istnieje wynik w jednym z podanych statusow. */
    @Query("""
            select distinct r.orderItemId from LabResult r
            where r.orderId = :orderId and r.orderItemId is not null and r.status in :statuses
            """)
    List<UUID> orderItemIdsWithStatus(@Param("orderId") UUID orderId,
            @Param("statuses") Collection<ResultStatus> statuses);

    boolean existsByOrderItemIdAndStatusIn(UUID orderItemId, Collection<ResultStatus> statuses);
}
