# Meeting Room Booking Service

A Spring Boot REST API for managing meeting room bookings with idempotency, cancellation, and utilization reporting.

## Prerequisites

- Java 17+
- Maven 3.8+

## Run the Application

```bash
cd meeting-room-booking
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

H2 console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/bookingdb`).

## Run Tests

```bash
mvn test
```

## API Endpoints

| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| POST   | `/rooms`                      | Create a room            |
| GET    | `/rooms`                      | List rooms (filters: `minCapacity`, `amenity`) |
| POST   | `/bookings`                   | Create a booking (header: `Idempotency-Key`) |
| GET    | `/bookings`                   | List bookings (filters: `roomId`, `from`, `to`, `limit`, `offset`) |
| POST   | `/bookings/{id}/cancel`       | Cancel a booking         |
| GET    | `/reports/room-utilization`   | Room utilization report (`from`, `to` required) |

## Quick Test with cURL

```bash
# Create a room
curl -X POST http://localhost:8080/rooms \
  -H "Content-Type: application/json" \
  -d '{"name":"Alpha","capacity":10,"floor":1,"amenities":["whiteboard","projector"]}'

# Create a booking (next Monday 09:00-10:00 UTC — adjust date)
curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-123" \
  -d '{"roomId":1,"title":"Standup","organizerEmail":"dev@example.com","startTime":"2025-06-02T09:00:00Z","endTime":"2025-06-02T10:00:00Z"}'

# List bookings
curl http://localhost:8080/bookings?limit=10&offset=0

# Cancel
curl -X POST http://localhost:8080/bookings/1/cancel

# Utilization report
curl "http://localhost:8080/reports/room-utilization?from=2025-06-02T00:00:00Z&to=2025-06-07T00:00:00Z"
```

## Project Structure

```
src/main/java/com/everquint/booking/
├── MeetingRoomBookingApplication.java
├── controller/
│   ├── RoomController.java
│   ├── BookingController.java
│   └── ReportController.java
├── dto/
│   ├── RoomDto.java
│   ├── BookingDto.java
│   ├── RoomUtilizationDto.java
│   └── ErrorResponse.java
├── exception/
│   ├── AppExceptions.java
│   └── GlobalExceptionHandler.java
├── model/
│   ├── Room.java
│   ├── Booking.java
│   ├── BookingStatus.java
│   └── IdempotencyRecord.java
├── repository/
│   ├── RoomRepository.java
│   ├── BookingRepository.java
│   └── IdempotencyRecordRepository.java
└── service/
    ├── RoomService.java
    ├── BookingService.java
    └── UtilizationService.java
```

See [DESIGN.md](DESIGN.md) for architecture decisions and trade-offs.
