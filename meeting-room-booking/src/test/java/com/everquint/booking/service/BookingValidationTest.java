package com.everquint.booking.service;

import com.everquint.booking.exception.AppExceptions;
import com.everquint.booking.repository.BookingRepository;
import com.everquint.booking.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BookingValidationTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private IdempotencyRecordRepository idempotencyRepository;
    @Mock private RoomService roomService;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, idempotencyRepository, roomService);
    }

    private Instant nextMonday(int hour, int minute) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        return monday.atTime(hour, minute).atZone(ZoneId.of("UTC")).toInstant();
    }

    @Nested
    @DisplayName("Duration validation")
    class DurationTests {

        @Test
        @DisplayName("rejects duration under 15 minutes")
        void rejectsTooShort() {
            Instant start = nextMonday(9, 0);
            Instant end = nextMonday(9, 10); // 10 min
            var ex = assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(start, end));
            assertTrue(ex.getMessage().contains("at least 15 minutes"));
        }

        @Test
        @DisplayName("rejects duration over 4 hours")
        void rejectsTooLong() {
            Instant start = nextMonday(9, 0);
            Instant end = nextMonday(13, 15); // 4h 15m
            var ex = assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(start, end));
            assertTrue(ex.getMessage().contains("not exceed 4 hours"));
        }

        @Test
        @DisplayName("accepts exactly 15 minutes")
        void acceptsMin() {
            Instant start = nextMonday(9, 0);
            Instant end = nextMonday(9, 15);
            assertDoesNotThrow(() -> bookingService.validateBookingRules(start, end));
        }

        @Test
        @DisplayName("accepts exactly 4 hours")
        void acceptsMax() {
            Instant start = nextMonday(9, 0);
            Instant end = nextMonday(13, 0);
            assertDoesNotThrow(() -> bookingService.validateBookingRules(start, end));
        }
    }

    @Nested
    @DisplayName("Time ordering")
    class TimeOrderTests {

        @Test
        @DisplayName("rejects start == end")
        void rejectsEqual() {
            Instant t = nextMonday(10, 0);
            assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(t, t));
        }

        @Test
        @DisplayName("rejects start > end")
        void rejectsReversed() {
            assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(nextMonday(11, 0), nextMonday(10, 0)));
        }
    }

    @Nested
    @DisplayName("Working hours validation")
    class WorkingHoursTests {

        @Test
        @DisplayName("rejects Saturday booking")
        void rejectsSaturday() {
            LocalDate monday = LocalDate.now()
                    .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
            LocalDate saturday = monday.plusDays(5);
            Instant start = saturday.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant();
            Instant end = saturday.atTime(11, 0).atZone(ZoneId.of("UTC")).toInstant();

            var ex = assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(start, end));
            assertTrue(ex.getMessage().contains("Monday to Friday"));
        }

        @Test
        @DisplayName("rejects booking before 08:00")
        void rejectsTooEarly() {
            Instant start = nextMonday(7, 0);
            Instant end = nextMonday(8, 0);
            var ex = assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(start, end));
            assertTrue(ex.getMessage().contains("08:00 and 20:00"));
        }

        @Test
        @DisplayName("rejects booking after 20:00")
        void rejectsTooLate() {
            Instant start = nextMonday(19, 0);
            Instant end = nextMonday(20, 30);
            var ex = assertThrows(AppExceptions.ValidationException.class,
                    () -> bookingService.validateBookingRules(start, end));
            assertTrue(ex.getMessage().contains("08:00 and 20:00"));
        }

        @Test
        @DisplayName("accepts booking ending exactly at 20:00")
        void acceptsBoundary() {
            Instant start = nextMonday(19, 0);
            Instant end = nextMonday(20, 0);
            assertDoesNotThrow(() -> bookingService.validateBookingRules(start, end));
        }

        @Test
        @DisplayName("accepts booking starting at 08:00")
        void acceptsStart() {
            Instant start = nextMonday(8, 0);
            Instant end = nextMonday(9, 0);
            assertDoesNotThrow(() -> bookingService.validateBookingRules(start, end));
        }
    }
}
