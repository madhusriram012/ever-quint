package com.everquint.booking.service;

import com.everquint.booking.dto.BookingDto;
import com.everquint.booking.exception.AppExceptions;
import com.everquint.booking.model.*;
import com.everquint.booking.repository.BookingRepository;
import com.everquint.booking.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RoomService roomService;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("UTC");
    private static final int BIZ_HOUR_START = 8;
    private static final int BIZ_HOUR_END = 20;
    private static final long MIN_DURATION_MINUTES = 15;
    private static final long MAX_DURATION_MINUTES = 240;

    @Transactional
    public BookingDto.Response createBooking(BookingDto.CreateRequest req, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing =
                    idempotencyRepository.findByIdempotencyKeyAndOrganizerEmail(
                            idempotencyKey, req.getOrganizerEmail());
            if (existing.isPresent()) {
                return toResponse(existing.get().getBooking());
            }
        }

        Room room = roomService.findById(req.getRoomId());

        validateBookingRules(req.getStartTime(), req.getEndTime());

        if (bookingRepository.existsOverlap(room.getId(), req.getStartTime(), req.getEndTime())) {
            throw new AppExceptions.ConflictException(
                    "Booking overlaps with an existing confirmed booking for this room");
        }

        Booking booking = Booking.builder()
                .room(room)
                .title(req.getTitle())
                .organizerEmail(req.getOrganizerEmail())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .status(BookingStatus.CONFIRMED)
                .build();

        booking = bookingRepository.save(booking);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyRepository.save(IdempotencyRecord.builder()
                        .idempotencyKey(idempotencyKey)
                        .organizerEmail(req.getOrganizerEmail())
                        .booking(booking)
                        .build());
            } catch (DataIntegrityViolationException e) {
                return idempotencyRepository
                        .findByIdempotencyKeyAndOrganizerEmail(idempotencyKey, req.getOrganizerEmail())
                        .map(rec -> toResponse(rec.getBooking()))
                        .orElseThrow(() -> new AppExceptions.ConflictException(
                                "Concurrent idempotent request conflict"));
            }
        }

        return toResponse(booking);
    }

    void validateBookingRules(Instant startTime, Instant endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new AppExceptions.ValidationException("startTime must be before endTime");
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes < MIN_DURATION_MINUTES) {
            throw new AppExceptions.ValidationException(
                    "Booking duration must be at least 15 minutes");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new AppExceptions.ValidationException(
                    "Booking duration must not exceed 4 hours");
        }

        validateWorkingHours(startTime);
        validateWorkingHours(endTime);
    }

    private void validateWorkingHours(Instant instant) {
        ZonedDateTime zdt = instant.atZone(BUSINESS_ZONE);
        DayOfWeek day = zdt.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new AppExceptions.ValidationException(
                    "Bookings are only allowed Monday to Friday");
        }

        LocalTime time = zdt.toLocalTime();
        if (time.isBefore(LocalTime.of(BIZ_HOUR_START, 0)) ||
                time.isAfter(LocalTime.of(BIZ_HOUR_END, 0))) {
            throw new AppExceptions.ValidationException(
                    "Bookings are only allowed between 08:00 and 20:00");
        }
    }

    @Transactional(readOnly = true)
    public BookingDto.PaginatedResponse listBookings(Long roomId, Instant from, Instant to,
                                                      int limit, int offset) {
        List<Booking> all = bookingRepository.findFiltered(roomId, from, to);
        long total = all.size();

        List<BookingDto.Response> items = all.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toResponse)
                .toList();

        return BookingDto.PaginatedResponse.builder()
                .items(items)
                .total(total)
                .limit(limit)
                .offset(offset)
                .build();
    }

    @Transactional
    public BookingDto.Response cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppExceptions.NotFoundException(
                        "Booking not found with id: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toResponse(booking);
        }

        Instant cutoff = booking.getStartTime().minus(Duration.ofHours(1));
        if (Instant.now().isAfter(cutoff)) {
            throw new AppExceptions.ValidationException(
                    "Cannot cancel booking less than 1 hour before start time");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking = bookingRepository.save(booking);
        return toResponse(booking);
    }

    BookingDto.Response toResponse(Booking booking) {
        return BookingDto.Response.builder()
                .id(booking.getId())
                .roomId(booking.getRoom().getId())
                .title(booking.getTitle())
                .organizerEmail(booking.getOrganizerEmail())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus().name().toLowerCase())
                .build();
    }
}
