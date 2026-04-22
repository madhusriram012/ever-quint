package com.everquint.booking.repository;

import com.everquint.booking.model.Booking;
import com.everquint.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.room.id = :roomId " +
           "AND b.status = 'CONFIRMED' " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    boolean existsOverlap(@Param("roomId") Long roomId,
                           @Param("startTime") Instant startTime,
                           @Param("endTime") Instant endTime);

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId " +
           "AND b.status = 'CONFIRMED' " +
           "AND b.startTime < :to AND b.endTime > :from")
    List<Booking> findConfirmedInRange(@Param("roomId") Long roomId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    @Query("SELECT b FROM Booking b WHERE " +
           "(:roomId IS NULL OR b.room.id = :roomId) " +
           "AND (:from IS NULL OR b.endTime > :from) " +
           "AND (:to IS NULL OR b.startTime < :to) " +
           "ORDER BY b.startTime ASC")
    List<Booking> findFiltered(@Param("roomId") Long roomId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
