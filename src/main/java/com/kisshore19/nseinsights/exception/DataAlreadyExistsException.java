package com.kisshore19.nseinsights.exception;

import java.time.LocalDate;

public class DataAlreadyExistsException extends RuntimeException {
    private final LocalDate tradeDate;
    private final int existingRecords;

    public DataAlreadyExistsException(LocalDate tradeDate, int existingRecords) {
        super("Data for " + tradeDate + " already downloaded with " + existingRecords + " records.");
        this.tradeDate = tradeDate;
        this.existingRecords = existingRecords;
    }

    public LocalDate getTradeDate() { return tradeDate; }
    public int getExistingRecords() { return existingRecords; }
}
