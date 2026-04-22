package com.everquint.booking.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomUtilizationDto {
    private Long roomId;
    private String roomName;
    private double totalBookingHours;
    private double utilizationPercent;
}
