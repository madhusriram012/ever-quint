package com.everquint.booking.service;

import com.everquint.booking.dto.RoomDto;
import com.everquint.booking.exception.AppExceptions;
import com.everquint.booking.model.Room;
import com.everquint.booking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    @Transactional
    public RoomDto.Response createRoom(RoomDto.CreateRequest req) {
        roomRepository.findByNameLower(req.getName().toLowerCase())
                .ifPresent(existing -> {
                    throw new AppExceptions.ConflictException(
                            "A room with name '" + req.getName() + "' already exists");
                });

        Room room = Room.builder()
                .name(req.getName())
                .capacity(req.getCapacity())
                .floor(req.getFloor())
                .amenities(req.getAmenities() != null ? req.getAmenities() : Collections.emptyList())
                .build();

        room = roomRepository.save(room);
        return toResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomDto.Response> listRooms(Integer minCapacity, String amenity) {
        List<Room> rooms = roomRepository.findWithFilters(minCapacity);

        if (amenity != null && !amenity.isBlank()) {
            String lower = amenity.toLowerCase();
            rooms = rooms.stream()
                    .filter(r -> r.getAmenities().stream()
                            .anyMatch(a -> a.toLowerCase().contains(lower)))
                    .toList();
        }

        return rooms.stream().map(this::toResponse).toList();
    }

    public Room findById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new AppExceptions.NotFoundException("Room not found with id: " + id));
    }

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    private RoomDto.Response toResponse(Room room) {
        return RoomDto.Response.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .floor(room.getFloor())
                .amenities(room.getAmenities())
                .build();
    }
}
