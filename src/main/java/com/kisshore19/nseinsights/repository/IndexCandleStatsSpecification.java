package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.entity.IndexCandleStats;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class IndexCandleStatsSpecification implements Specification<IndexCandleStats> {

    private final ScreenerRequest req;

    public IndexCandleStatsSpecification(ScreenerRequest req) {
        this.req = req;
    }

    @Override
    public Predicate toPredicate(Root<IndexCandleStats> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        List<Predicate> predicates = new ArrayList<>();

        // Required: scope by timeframe + periodKey
        predicates.add(cb.equal(root.get("timeframe"), req.getTimeframe()));
        predicates.add(cb.equal(root.get("periodKey"), req.getPeriod()));

        // Close value range
        if (req.getMinClose() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("lastValue"), req.getMinClose()));
        }
        if (req.getMaxClose() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("lastValue"), req.getMaxClose()));
        }

        // Trend
        if (req.getTrend() != null && !req.getTrend().isBlank()) {
            predicates.add(cb.equal(root.get("trend"), req.getTrend().toUpperCase()));
        }

        // High volume threshold
        if (req.getMinHighVol() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("highVolQty"), req.getMinHighVol().longValue()));
        }

        // % change: ((lastValue - openValue) / openValue) * 100
        if (req.getMinPctChange() != null || req.getMaxPctChange() != null) {
            Expression<BigDecimal> open  = root.get("openValue");
            Expression<BigDecimal> close = root.get("lastValue");
            Expression<BigDecimal> pct   = cb.prod(
                    cb.quot(cb.diff(close, open), open).as(BigDecimal.class),
                    BigDecimal.valueOf(100));

            if (req.getMinPctChange() != null) {
                predicates.add(cb.greaterThanOrEqualTo(pct, req.getMinPctChange()));
            }
            if (req.getMaxPctChange() != null) {
                predicates.add(cb.lessThanOrEqualTo(pct, req.getMaxPctChange()));
            }
        }

        // High value date position (EARLY / LATE)
        if (req.getHighDatePos() != null && !req.getHighDatePos().isBlank()) {
            Expression<Integer> daysFromStart = cb.function(
                    "DATEDIFF", Integer.class,
                    root.get("highValueDate"), root.get("candleDate"));
            Expression<Integer> halfPeriod = cb.quot(
                    cb.function("DATEDIFF", Integer.class,
                            root.get("candleEndDate"), root.get("candleDate")),
                    2).as(Integer.class);

            if ("EARLY".equalsIgnoreCase(req.getHighDatePos())) {
                predicates.add(cb.lessThanOrEqualTo(daysFromStart, halfPeriod));
            } else if ("LATE".equalsIgnoreCase(req.getHighDatePos())) {
                predicates.add(cb.greaterThan(daysFromStart, halfPeriod));
            }
        }

        // Low value date position (EARLY / LATE)
        if (req.getLowDatePos() != null && !req.getLowDatePos().isBlank()) {
            Expression<Integer> daysFromStart = cb.function(
                    "DATEDIFF", Integer.class,
                    root.get("lowValueDate"), root.get("candleDate"));
            Expression<Integer> halfPeriod = cb.quot(
                    cb.function("DATEDIFF", Integer.class,
                            root.get("candleEndDate"), root.get("candleDate")),
                    2).as(Integer.class);

            if ("EARLY".equalsIgnoreCase(req.getLowDatePos())) {
                predicates.add(cb.lessThanOrEqualTo(daysFromStart, halfPeriod));
            } else if ("LATE".equalsIgnoreCase(req.getLowDatePos())) {
                predicates.add(cb.greaterThan(daysFromStart, halfPeriod));
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
