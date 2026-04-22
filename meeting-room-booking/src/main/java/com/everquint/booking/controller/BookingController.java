package com.everquint.booking.controller;

import com.everquint.booking.dto.BookingDto;
import com.everquint.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto.Response create(
            @Valid @RequestBody BookingDto.CreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return bookingService.createBooking(request, idempotencyKey);
    }

    @GetMapping
    public BookingDto.PaginatedResponse list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return bookingService.listBookings(roomId, from, to, limit, offset);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto.Response cancel(@PathVariable Long id) {
        return bookingService.cancelBooking(id);
    }
}
