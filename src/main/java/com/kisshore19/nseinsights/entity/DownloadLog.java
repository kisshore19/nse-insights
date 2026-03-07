package com.kisshore19.nseinsights.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "download_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "bhavacopy_url", length = 500)
    private String bhavatopyUrl;

    @Column(name = "mto_url", length = 500)
    private String mtoUrl;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "downloaded_at", nullable = false)
    private LocalDateTime downloadedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.downloadedAt = LocalDateTime.now();
    }

    // Convenience status constants
    public static final String STATUS_SUCCESS  = "SUCCESS";
    public static final String STATUS_FAILED   = "FAILED";
    public static final String STATUS_DELETED  = "DELETED";
    public static final String STATUS_PARTIAL  = "PARTIAL";
}
