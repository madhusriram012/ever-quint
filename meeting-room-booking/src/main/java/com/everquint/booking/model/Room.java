package com.everquint.booking.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "rooms", uniqueConstraints = {
    @UniqueConstraint(name = "uk_room_name_lower", columnNames = "nameLower")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nameLower;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int floor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity")
    private List<String> amenities;

    @PrePersist
    @PreUpdate
    void normalizeName() {
        this.nameLower = (this.name != null) ? this.name.toLowerCase() : null;
    }
}
