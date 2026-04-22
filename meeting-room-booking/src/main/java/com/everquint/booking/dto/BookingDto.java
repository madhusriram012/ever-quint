package com.everquint.booking.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;

public class BookingDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotNull(message = "roomId is required")
        private Long roomId;

        @NotBlank(message = "title is required")
        private String title;

        @NotBlank(message = "organizerEmail is required")
        @Email(message = "organizerEmail must be a valid email")
        private String organizerEmail;

        @NotNull(message = "startTime is required")
        private Instant startTime;

        @NotNull(message = "endTime is required")
        private Instant endTime;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private Long roomId;
        private String title;
        private String organizerEmail;
        private Instant startTime;
        private Instant endTime;
        private String status;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PaginatedResponse {
        private java.util.List<Response> items;
        private long total;
        private int limit;
        private int offset;
    }
}
