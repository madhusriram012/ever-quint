package com.everquint.booking.repository;

import com.everquint.booking.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByNameLower(String nameLower);

    @Query("SELECT r FROM Room r WHERE " +
           "(:minCapacity IS NULL OR r.capacity >= :minCapacity)")
    List<Room> findWithFilters(@Param("minCapacity") Integer minCapacity);
}
