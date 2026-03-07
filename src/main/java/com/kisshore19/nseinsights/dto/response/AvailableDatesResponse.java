package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Response for available trading dates API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailableDatesResponse {

    private List<LocalDate> dates;
    private int totalDates;
    private LocalDate latestDate;
    private LocalDate oldestDate;
}
