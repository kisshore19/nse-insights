package com.kisshore19.nseinsights.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String status;
    private String message;
    private String errorCode;
    private T data;

    // ── Static factory methods ─────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> alreadyExists(String message, T data) {
        return ApiResponse.<T>builder()
                .status("ALREADY_EXISTS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
