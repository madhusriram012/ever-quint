package com.everquint.booking.controller;

import com.everquint.booking.dto.RoomUtilizationDto;
import com.everquint.booking.exception.AppExceptions;
import com.everquint.booking.service.UtilizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final UtilizationService utilizationService;

    @GetMapping("/room-utilization")
    public List<RoomUtilizationDto> utilization(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        if (from == null || to == null) {
            throw new AppExceptions.ValidationException("'from' and 'to' are required");
        }
        if (!from.isBefore(to)) {
            throw new AppExceptions.ValidationException("'from' must be before 'to'");
        }
        return utilizationService.getUtilization(from, to);
    }
}
