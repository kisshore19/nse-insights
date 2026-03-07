package com.kisshore19.nseinsights.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequest {

    @NotNull(message = "fromTradeDate is required")
    @PastOrPresent(message = "Cannot download data for a future date")
    private LocalDate fromTradeDate;

    @PastOrPresent(message = "Cannot download data for a future date")
    private LocalDate toTradeDate;  // if null, downloads only fromTradeDate

    private boolean overwrite = false;

    /**
     * Returns true if this is a date range request (fromDate != toDate)
     */
    public boolean isRangeRequest() {
        return toTradeDate != null && !toTradeDate.equals(fromTradeDate);
    }

    /**
     * Returns toTradeDate if provided, otherwise fromTradeDate (single date)
     */
    public LocalDate getEffectiveToDate() {
        return toTradeDate != null ? toTradeDate : fromTradeDate;
    }
}