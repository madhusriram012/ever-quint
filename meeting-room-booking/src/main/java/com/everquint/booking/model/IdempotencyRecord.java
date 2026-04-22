package com.everquint.booking.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(name = "uk_idempotency", columnNames = {"idempotencyKey", "organizerEmail"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String organizerEmail;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
