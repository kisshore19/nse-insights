package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.List;

/**
 * Response for sectors list API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectorsResponse {

    private List<String> sectors;
    private int totalSectors;
}
