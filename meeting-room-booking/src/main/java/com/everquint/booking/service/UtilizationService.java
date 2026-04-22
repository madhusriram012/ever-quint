package com.everquint.booking.service;

import com.everquint.booking.dto.RoomUtilizationDto;
import com.everquint.booking.model.Booking;
import com.everquint.booking.model.Room;
import com.everquint.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilizationService {

    private final BookingRepository bookingRepository;
    private final RoomService roomService;

    private static final ZoneId BIZ_ZONE = ZoneId.of("UTC");
    private static final int BIZ_START = 8;
    private static final int BIZ_END = 20;
    private static final double BIZ_HOURS_PER_DAY = BIZ_END - BIZ_START; // 12

    @Transactional(readOnly = true)
    public List<RoomUtilizationDto> getUtilization(Instant from, Instant to) {
        double totalBizHours = calculateTotalBusinessHours(from, to);
        List<Room> rooms = roomService.findAll();
        List<RoomUtilizationDto> results = new ArrayList<>();

        for (Room room : rooms) {
            List<Booking> bookings = bookingRepository.findConfirmedInRange(room.getId(), from, to);
            double bookedHours = calculateBookedHours(bookings, from, to);
            double utilization = (totalBizHours > 0) ? bookedHours / totalBizHours : 0.0;

            results.add(RoomUtilizationDto.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .totalBookingHours(Math.round(bookedHours * 100.0) / 100.0)
                    .utilizationPercent(Math.round(utilization * 100.0) / 100.0)
                    .build());
        }
        return results;
    }

    double calculateBookedHours(List<Booking> bookings, Instant from, Instant to) {
        double total = 0;
        for (Booking b : bookings) {
            Instant effectiveStart = b.getStartTime().isBefore(from) ? from : b.getStartTime();
            Instant effectiveEnd = b.getEndTime().isAfter(to) ? to : b.getEndTime();

            if (effectiveStart.isBefore(effectiveEnd)) {
                total += businessHoursBetween(effectiveStart, effectiveEnd);
            }
        }
        return total;
    }

    double calculateTotalBusinessHours(Instant from, Instant to) {
        LocalDate startDate = from.atZone(BIZ_ZONE).toLocalDate();
        LocalDate endDate = to.atZone(BIZ_ZONE).toLocalDate();
        if (to.atZone(BIZ_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            endDate = endDate.minusDays(1);
        }

        double total = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;

            Instant dayBizStart = d.atTime(BIZ_START, 0).atZone(BIZ_ZONE).toInstant();
            Instant dayBizEnd = d.atTime(BIZ_END, 0).atZone(BIZ_ZONE).toInstant();

            Instant effectiveStart = dayBizStart.isBefore(from) ? from : dayBizStart;
            Instant effectiveEnd = dayBizEnd.isAfter(to) ? to : dayBizEnd;

            if (effectiveStart.isBefore(effectiveEnd)) {
                total += Duration.between(effectiveStart, effectiveEnd).toMinutes() / 60.0;
            }
        }
        return total;
    }

    private double businessHoursBetween(Instant start, Instant end) {
        LocalDate startDate = start.atZone(BIZ_ZONE).toLocalDate();
        LocalDate endDate = end.atZone(BIZ_ZONE).toLocalDate();

        double total = 0;
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;

            Instant dayBizStart = d.atTime(BIZ_START, 0).atZone(BIZ_ZONE).toInstant();
            Instant dayBizEnd = d.atTime(BIZ_END, 0).atZone(BIZ_ZONE).toInstant();

            Instant s = start.isAfter(dayBizStart) ? start : dayBizStart;
            Instant e = end.isBefore(dayBizEnd) ? end : dayBizEnd;

            if (s.isBefore(e)) {
                total += Duration.between(s, e).toMinutes() / 60.0;
            }
        }
        return total;
    }
}
