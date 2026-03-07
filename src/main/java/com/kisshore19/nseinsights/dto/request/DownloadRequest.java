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

    @NotNull(message = "tradeDate is required")
    @PastOrPresent(message = "Cannot download data for a future date")
    private LocalDate tradeDate;

    private boolean overwrite = false;
}
