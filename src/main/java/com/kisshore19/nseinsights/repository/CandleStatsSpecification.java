package com.kisshore19.nseinsights.repository;

import com.kisshore19.nseinsights.dto.request.ScreenerRequest;
import com.kisshore19.nseinsights.entity.CandleStats;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JPA Specification dynamically from ScreenerRequest filters.
 * Each filter is only applied if the corresponding param is non-null.
 * All filtering happens directly on candle_stats columns — no joins needed.
 */
public class CandleStatsSpecification implements Specification<CandleStats> {

    private static final long CR = 1_00_00_000L; // 1 Crore

    private final ScreenerRequest req;

    public CandleStatsSpecification(ScreenerRequest req) {
        this.req = req;
    }

    @Override
    public Predicate toPredicate(Root<CandleStats> root,
                                 CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {

        List<Predicate> predicates = new ArrayList<>();

        // ── Required: scope by timeframe + periodKey ──────────────────────────
        predicates.add(cb.equal(root.get("timeframe"), req.getTimeframe()));
        predicates.add(cb.equal(root.get("periodKey"), req.getPeriod()));

        // ── Close price range ─────────────────────────────────────────────────
        if (req.getMinClose() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("lastPrice"), req.getMinClose()));
        }
        if (req.getMaxClose() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("lastPrice"), req.getMaxClose()));
        }

        // ── Trend ─────────────────────────────────────────────────────────────
        if (req.getTrend() != null && !req.getTrend().isBlank()) {
            predicates.add(cb.equal(root.get("trend"), req.getTrend().toUpperCase()));
        }

        // ── High volume threshold (converted from crores to units) ─────────────
        if (req.getMinHighVol() != null) {
            long threshold = req.getMinHighVol().multiply(BigDecimal.valueOf(CR))
                               .setScale(0, RoundingMode.HALF_UP).longValue();
            predicates.add(cb.greaterThanOrEqualTo(root.get("highVolQty"), threshold));
        }

        // ── High delivery threshold (converted from crores to units) ───────────
        if (req.getMinHighDeliv() != null) {
            long threshold = req.getMinHighDeliv().multiply(BigDecimal.valueOf(CR))
                                .setScale(0, RoundingMode.HALF_UP).longValue();
            predicates.add(cb.greaterThanOrEqualTo(root.get("highDelivQty"), threshold));
        }

        // ── % change intra-period: ((lastPrice - openPrice) / openPrice) * 100 ─
        // Expressed as a JPA criteria expression — no stored column needed
        if (req.getMinPctChange() != null || req.getMaxPctChange() != null) {
            Expression<BigDecimal> open  = root.get("openPrice");
            Expression<BigDecimal> close = root.get("lastPrice");

            // pctChange = ((close - open) / open) * 100
            Expression<BigDecimal> diff    = cb.diff(close, open);
            Expression<BigDecimal> pctExpr = cb.prod(
                    cb.quot(diff, open).as(BigDecimal.class),
                    BigDecimal.valueOf(100)
            );

            if (req.getMinPctChange() != null) {
                predicates.add(cb.greaterThanOrEqualTo(pctExpr, req.getMinPctChange()));
            }
            if (req.getMaxPctChange() != null) {
                predicates.add(cb.lessThanOrEqualTo(pctExpr, req.getMaxPctChange()));
            }
        }

        // ── Delivery % of volume: (highDelivQty / highVolQty) * 100 ───────────
        if (req.getMinDelivPct() != null) {
            Expression<Long>       deliv   = root.get("highDelivQty");
            Expression<Long>       vol     = root.get("highVolQty");
            Expression<BigDecimal> pctExpr = null; /*cb.prod(
                    cb.quot(deliv.as(BigDecimal.class), vol.as(BigDecimal.class)),
                    BigDecimal.valueOf(100)
            );*/
            predicates.add(cb.greaterThanOrEqualTo(pctExpr, req.getMinDelivPct()));
        }

        // ── High price date position: EARLY or LATE in the period ─────────────
        // midPoint = candleDate + (candleEndDate - candleDate) / 2
        // EARLY = highPriceDate <= midPoint
        // LATE  = highPriceDate >  midPoint
        // Expressed via DATEDIFF: days from start to highPriceDate vs half period length
        if (req.getHighDatePos() != null && !req.getHighDatePos().isBlank()) {
            Expression<Integer> daysFromStart = cb.function(
                    "DATEDIFF", Integer.class,
                    root.get("highPriceDate"), root.get("candleDate"));
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

        // ── Low price date position: EARLY or LATE in the period ──────────────
        if (req.getLowDatePos() != null && !req.getLowDatePos().isBlank()) {
            Expression<Integer> daysFromStart = cb.function(
                    "DATEDIFF", Integer.class,
                    root.get("lowPriceDate"), root.get("candleDate"));
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