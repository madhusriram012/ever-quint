package com.everquint.booking.repository;

import com.everquint.booking.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndOrganizerEmail(String key, String email);
}
