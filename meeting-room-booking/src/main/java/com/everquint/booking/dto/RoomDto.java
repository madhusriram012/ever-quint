package com.everquint.booking.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

public class RoomDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank(message = "name is required")
        private String name;

        @Min(value = 1, message = "capacity must be at least 1")
        private int capacity;

        private int floor;

        private List<String> amenities;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String name;
        private int capacity;
        private int floor;
        private List<String> amenities;
    }
}
