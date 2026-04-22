package com.everquint.booking.controller;

import com.everquint.booking.dto.BookingDto;
import com.everquint.booking.dto.RoomDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    private static final ZoneId UTC = ZoneId.of("UTC");

    private Instant nextMonday(int hour, int minute) {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return monday.atTime(hour, minute).atZone(UTC).toInstant();
    }

    private Long createRoom(String name, int capacity) throws Exception {
        var req = RoomDto.CreateRequest.builder()
                .name(name).capacity(capacity).floor(1)
                .amenities(List.of("whiteboard", "projector"))
                .build();

        MvcResult result = mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ── Room Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /rooms - create and list")
    void createAndListRooms() throws Exception {
        createRoom("Alpha", 10);

        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Alpha")));
    }

    @Test
    @DisplayName("POST /rooms - duplicate name rejected (case-insensitive)")
    void duplicateRoomName() throws Exception {
        createRoom("Alpha", 10);

        var req = RoomDto.CreateRequest.builder()
                .name("ALPHA").capacity(5).floor(2).amenities(List.of()).build();
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("ConflictError")));
    }

    @Test
    @DisplayName("GET /rooms - filter by minCapacity")
    void filterByCapacity() throws Exception {
        createRoom("Small", 4);
        createRoom("Big", 20);

        mvc.perform(get("/rooms").param("minCapacity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Big")));
    }

    @Test
    @DisplayName("GET /rooms - filter by amenity")
    void filterByAmenity() throws Exception {
        createRoom("WithBoard", 5);

        mvc.perform(get("/rooms").param("amenity", "whiteboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ── Booking Happy Path ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /bookings - happy path")
    void createBookingSuccess() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Standup")
                .organizerEmail("test@example.com")
                .startTime(nextMonday(9, 0))
                .endTime(nextMonday(10, 0))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("confirmed")))
                .andExpect(jsonPath("$.roomId", is(roomId.intValue())));
    }

    // ── Booking Conflict ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /bookings - overlap returns 409")
    void overlapConflict() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req1 = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Meeting A")
                .organizerEmail("a@test.com")
                .startTime(nextMonday(9, 0))
                .endTime(nextMonday(10, 0))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        var req2 = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Meeting B")
                .organizerEmail("b@test.com")
                .startTime(nextMonday(9, 30))
                .endTime(nextMonday(10, 30))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("ConflictError")));
    }

    @Test
    @DisplayName("POST /bookings - unknown room returns 404")
    void unknownRoom() throws Exception {
        var req = BookingDto.CreateRequest.builder()
                .roomId(999L).title("Ghost")
                .organizerEmail("x@test.com")
                .startTime(nextMonday(9, 0))
                .endTime(nextMonday(10, 0))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── Idempotency ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /bookings - idempotent: same key returns same booking")
    void idempotentBooking() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Standup")
                .organizerEmail("test@example.com")
                .startTime(nextMonday(9, 0))
                .endTime(nextMonday(10, 0))
                .build();

        String body = mapper.writeValueAsString(req);

        MvcResult first = mvc.perform(post("/bookings")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mvc.perform(post("/bookings")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        long id1 = mapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        long id2 = mapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        Assertions.assertEquals(id1, id2, "Idempotent calls must return the same booking");
    }

    // ── Cancellation ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /bookings/{id}/cancel - success when >1h before start")
    void cancelSuccess() throws Exception {
        Long roomId = createRoom("Room1", 10);

        Instant futureStart = nextMonday(14, 0);
        Instant futureEnd = nextMonday(15, 0);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Future")
                .organizerEmail("test@example.com")
                .startTime(futureStart).endTime(futureEnd)
                .build();

        MvcResult result = mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long bookingId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("cancelled")));
    }

    @Test
    @DisplayName("POST /bookings/{id}/cancel - already cancelled is no-op")
    void cancelIdempotent() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Future")
                .organizerEmail("test@example.com")
                .startTime(nextMonday(14, 0)).endTime(nextMonday(15, 0))
                .build();

        MvcResult result = mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long bookingId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk());
        mvc.perform(post("/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("cancelled")));
    }

    @Test
    @DisplayName("Cancelled booking does NOT block new bookings")
    void cancelledDoesNotBlock() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Original")
                .organizerEmail("test@example.com")
                .startTime(nextMonday(9, 0)).endTime(nextMonday(10, 0))
                .build();

        MvcResult result = mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        long bookingId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/bookings/" + bookingId + "/cancel"))
                .andExpect(status().isOk());

        var req2 = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Replacement")
                .organizerEmail("other@example.com")
                .startTime(nextMonday(9, 0)).endTime(nextMonday(10, 0))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("confirmed")));
    }

    // ── List Bookings ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /bookings - pagination")
    void listBookingsPagination() throws Exception {
        Long roomId = createRoom("Room1", 10);

        for (int h = 8; h < 12; h++) {
            var req = BookingDto.CreateRequest.builder()
                    .roomId(roomId).title("Meeting " + h)
                    .organizerEmail("test@example.com")
                    .startTime(nextMonday(h, 0)).endTime(nextMonday(h + 1, 0))
                    .build();
            mvc.perform(post("/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/bookings")
                        .param("limit", "2").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(4)))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    // ── Utilization Report ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /reports/room-utilization - basic calculation")
    void utilizationReport() throws Exception {
        Long roomId = createRoom("Room1", 10);

        var req = BookingDto.CreateRequest.builder()
                .roomId(roomId).title("Long Meeting")
                .organizerEmail("test@example.com")
                .startTime(nextMonday(9, 0))
                .endTime(nextMonday(11, 0))
                .build();

        mvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String from = monday.atTime(0, 0).atZone(UTC).toInstant().toString();
        String to = monday.plusDays(1).atTime(0, 0).atZone(UTC).toInstant().toString();

        mvc.perform(get("/reports/room-utilization")
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalBookingHours", is(2.0)))
                .andExpect(jsonPath("$[0].utilizationPercent", closeTo(0.17, 0.01)));
    }
}
