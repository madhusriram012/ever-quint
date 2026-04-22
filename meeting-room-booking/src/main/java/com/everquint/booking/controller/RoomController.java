package com.everquint.booking.controller;

import com.everquint.booking.dto.RoomDto;
import com.everquint.booking.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto.Response create(@Valid @RequestBody RoomDto.CreateRequest request) {
        return roomService.createRoom(request);
    }

    @GetMapping
    public List<RoomDto.Response> list(
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String amenity) {
        return roomService.listRooms(minCapacity, amenity);
    }
}
