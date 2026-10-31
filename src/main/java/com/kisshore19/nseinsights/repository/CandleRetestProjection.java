package com.kisshore19.nseinsights.repository;

import java.time.LocalDate;

/**
 * One row per symbol for the retest query: when (and on how many days) the
 * daily price range of the target period touched the base period's
 * highest-volume / highest-delivery day range.
 */
public interface CandleRetestProjection {

    String     getSymbol();
    LocalDate  getVolTouchDate();     // first day touching the volume-day range
    Long       getVolTouchDays();     // number of days touching it
    LocalDate  getDelivTouchDate();   // first day touching the delivery-day range
    Long       getDelivTouchDays();

    // "yyyy-MM-dd|close" pairs, null when there is nothing to report:
    //   Approach = previous trading day close before the first touch ("date|" when unknown)
    //   After    = first day AFTER the target period's highest-volume day that closed outside the level
    String     getVolApproachRaw();
    String     getVolAfterRaw();
    String     getDelivApproachRaw();
    String     getDelivAfterRaw();
}
