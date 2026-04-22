package com.everquint.booking.service;

import com.everquint.booking.model.Booking;
import com.everquint.booking.model.BookingStatus;
import com.everquint.booking.model.Room;
import com.everquint.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UtilizationServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private RoomService roomService;

    private UtilizationService service;

    private static final ZoneId UTC = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        service = new UtilizationService(bookingRepository, roomService);
    }

    private Room testRoom() {
        return Room.builder().id(1L).name("Alpha").build();
    }

    private Booking booking(Instant start, Instant end) {
        return Booking.builder()
                .room(testRoom())
                .startTime(start).endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("single weekday = 12 business hours")
    void singleWeekday() {
        Instant from = LocalDate.of(2025, 6, 2).atStartOfDay(UTC).toInstant(); // Monday
        Instant to = LocalDate.of(2025, 6, 3).atStartOfDay(UTC).toInstant();   // Tuesday
        assertEquals(12.0, service.calculateTotalBusinessHours(from, to), 0.01);
    }

    @Test
    @DisplayName("full work week = 60 business hours")
    void fullWeek() {
        Instant from = LocalDate.of(2025, 6, 2).atStartOfDay(UTC).toInstant(); // Monday
        Instant to = LocalDate.of(2025, 6, 7).atStartOfDay(UTC).toInstant();   // Saturday
        assertEquals(60.0, service.calculateTotalBusinessHours(from, to), 0.01);
    }

    @Test
    @DisplayName("weekend only = 0 business hours")
    void weekendOnly() {
        Instant from = LocalDate.of(2025, 6, 7).atStartOfDay(UTC).toInstant(); // Saturday
        Instant to = LocalDate.of(2025, 6, 9).atStartOfDay(UTC).toInstant();   // Monday
        assertEquals(0.0, service.calculateTotalBusinessHours(from, to), 0.01);
    }

    @Test
    @DisplayName("no bookings = 0 booked hours")
    void noBookings() {
        Instant from = LocalDate.of(2025, 6, 2).atStartOfDay(UTC).toInstant();
        Instant to = LocalDate.of(2025, 6, 3).atStartOfDay(UTC).toInstant();
        assertEquals(0.0, service.calculateBookedHours(Collections.emptyList(), from, to));
    }

    @Test
    @DisplayName("1-hour booking within range")
    void simpleBooking() {
        Instant from = LocalDate.of(2025, 6, 2).atTime(8, 0).atZone(UTC).toInstant();
        Instant to = LocalDate.of(2025, 6, 2).atTime(20, 0).atZone(UTC).toInstant();
        Booking b = booking(
                LocalDate.of(2025, 6, 2).atTime(9, 0).atZone(UTC).toInstant(),
                LocalDate.of(2025, 6, 2).atTime(10, 0).atZone(UTC).toInstant());
        assertEquals(1.0, service.calculateBookedHours(List.of(b), from, to), 0.01);
    }

    @Test
    @DisplayName("booking partially before range is clamped")
    void partialOverlapStart() {
        Instant from = LocalDate.of(2025, 6, 2).atTime(10, 0).atZone(UTC).toInstant();
        Instant to = LocalDate.of(2025, 6, 2).atTime(20, 0).atZone(UTC).toInstant();
        Booking b = booking(
                LocalDate.of(2025, 6, 2).atTime(9, 0).atZone(UTC).toInstant(),
                LocalDate.of(2025, 6, 2).atTime(11, 0).atZone(UTC).toInstant());
        assertEquals(1.0, service.calculateBookedHours(List.of(b), from, to), 0.01);
    }

    @Test
    @DisplayName("booking partially after range is clamped")
    void partialOverlapEnd() {
        Instant from = LocalDate.of(2025, 6, 2).atTime(8, 0).atZone(UTC).toInstant();
        Instant to = LocalDate.of(2025, 6, 2).atTime(15, 0).atZone(UTC).toInstant();
        Booking b = booking(
                LocalDate.of(2025, 6, 2).atTime(14, 0).atZone(UTC).toInstant(),
                LocalDate.of(2025, 6, 2).atTime(17, 0).atZone(UTC).toInstant());
        assertEquals(1.0, service.calculateBookedHours(List.of(b), from, to), 0.01);
    }
}
