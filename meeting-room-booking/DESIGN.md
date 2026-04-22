# DESIGN.md — Meeting Room Booking Service

## 1. Data Model

### Entity Relationships

```
┌──────────────┐       ┌──────────────────┐       ┌─────────────────────┐
│     Room     │       │     Booking      │       │  IdempotencyRecord  │
├──────────────┤       ├──────────────────┤       ├─────────────────────┤
│ id (PK)      │◄──────│ room_id (FK)     │   ┌──►│ id (PK)             │
│ name         │  1:N  │ id (PK)          │◄──┘   │ idempotency_key     │
│ name_lower   │       │ title            │  1:1  │ organizer_email     │
│ capacity     │       │ organizer_email  │       │ booking_id (FK)     │
│ floor        │       │ start_time       │       │ created_at          │
└──────┬───────┘       │ end_time         │       └─────────────────────┘
       │               │ status           │       UQ(idempotency_key,
       │ 1:N           └──────────────────┘          organizer_email)
┌──────┴───────┐
│room_amenities│
├──────────────┤
│ room_id (FK) │
│ amenity      │
└──────────────┘
```

### Room

| Column     | Type          | Constraint          | Notes                                              |
|------------|---------------|---------------------|-----------------------------------------------------|
| id         | BIGINT        | PK, auto-increment  |                                                     |
| name       | VARCHAR(255)  | NOT NULL             | Original casing preserved for display               |
| name_lower | VARCHAR(255)  | NOT NULL, UNIQUE     | `name.toLowerCase()` — set via `@PrePersist` hook   |
| capacity   | INT           | NOT NULL             | Validated ≥ 1 at DTO layer (`@Min(1)`)              |
| floor      | INT           | NOT NULL             |                                                     |

**Why `name_lower`?** The spec requires case-insensitive uniqueness. Two alternatives were considered:

- **Option A**: DB-level `LOWER(name)` function index — clean, but not portable across H2/PostgreSQL without dialect-specific DDL.
- **Option B (chosen)**: Store a pre-computed lowercase column with a standard unique constraint. Works identically on H2 and PostgreSQL, enforced via `@PrePersist`/`@PreUpdate` lifecycle hook so application code can't forget.

**room_amenities** is a JPA `@ElementCollection` stored in a separate table. Tradeoff: simpler than a full `Amenity` entity, and the spec doesn't require amenity-level operations (CRUD, deduplication). If amenities needed to be first-class entities, this would change.

### Booking

| Column          | Type          | Constraint        | Notes                                            |
|-----------------|---------------|-------------------|--------------------------------------------------|
| id              | BIGINT        | PK, auto-increment|                                                  |
| room_id         | BIGINT        | FK → rooms.id     |                                                  |
| title           | VARCHAR(255)  | NOT NULL           |                                                  |
| organizer_email | VARCHAR(255)  | NOT NULL           | Validated as email at DTO layer                  |
| start_time      | TIMESTAMP     | NOT NULL           | Stored as UTC `Instant`                          |
| end_time        | TIMESTAMP     | NOT NULL           | Stored as UTC `Instant`                          |
| status          | VARCHAR(20)   | NOT NULL           | `CONFIRMED` or `CANCELLED`                       |

**Index**: `(room_id, start_time, end_time)` — this is the critical performance index. The overlap detection query filters by `room_id` first, then range-scans on `start_time`/`end_time`. Without this index, every booking creation would table-scan all bookings for the room.

**Why soft-delete (`status`) instead of hard-delete?** The spec requires cancelled bookings to be visible in listing endpoints and the cancellation endpoint to be idempotent (return the cancelled booking). Hard deletion would lose this data.

### IdempotencyRecord

| Column          | Type          | Constraint                            | Notes                              |
|-----------------|---------------|---------------------------------------|------------------------------------|
| id              | BIGINT        | PK, auto-increment                    |                                    |
| idempotency_key | VARCHAR(255)  | NOT NULL                              | Client-supplied via HTTP header    |
| organizer_email | VARCHAR(255)  | NOT NULL                              | Scoping dimension                  |
| booking_id      | BIGINT        | FK → bookings.id                      | The result of the original request |
| created_at      | TIMESTAMP     | NOT NULL                              | For future TTL/cleanup             |

**Unique constraint**: `(idempotency_key, organizer_email)` — this is the concurrency safety mechanism (explained in §5).

---

## 2. Overlap Prevention

### The Problem

Two bookings for the same room must not occupy any overlapping time window. There are four overlap cases between an existing booking `[S₁, E₁)` and a new booking `[S₂, E₂)`:

```
Case 1 — Partial left:     S₁──────E₁
                                S₂──────E₂

Case 2 — Partial right:        S₁──────E₁
                            S₂──────E₂

Case 3 — New contains old:     S₁──E₁
                            S₂──────────E₂

Case 4 — Old contains new: S₁──────────E₁
                                S₂──E₂
```

All four cases are captured by a single condition: `S₁ < E₂ AND E₁ > S₂`.

### The Implementation

```sql
-- JPQL executed inside BookingRepository
SELECT COUNT(b) > 0
FROM Booking b
WHERE b.room.id = :roomId
  AND b.status = 'CONFIRMED'        -- cancelled bookings are ignored
  AND b.startTime < :endTime
  AND b.endTime > :startTime
```

Key design decisions:

1. **Only CONFIRMED bookings are checked** — the spec explicitly states "cancelled bookings must not block new bookings." Filtering by status achieves this without any additional logic.

2. **Check-then-insert within `@Transactional`** — the overlap query and the booking insert happen in the same transaction. Under `READ_COMMITTED` isolation, this prevents the most common race conditions.

3. **Why not a DB-level exclusion constraint?** PostgreSQL supports `EXCLUDE USING gist` on range types, which would be ideal. However, H2 doesn't support this, and the spec allows H2. The application-level check is portable and sufficient for the assignment scope. The limitation is documented in §5.

---

## 3. Error Handling Strategy

### Principles

- **No business logic leaks into error responses** — error messages describe *what* went wrong, not *how* to fix it.
- **Consistent format everywhere** — every error, whether from validation annotations, business rules, or unexpected failures, produces the same JSON shape.
- **Appropriate HTTP semantics** — 400 for client mistakes, 404 for missing resources, 409 for state conflicts, 500 for bugs.

### Implementation

A single `@RestControllerAdvice` class (`GlobalExceptionHandler`) catches all exceptions and maps them:

| Source                          | Exception Class            | HTTP | `error` field   |
|---------------------------------|----------------------------|------|-----------------|
| Business rule violation         | `ValidationException`      | 400  | ValidationError |
| `@Valid` annotation failure     | `MethodArgumentNotValid`   | 400  | ValidationError |
| Room/Booking not found          | `NotFoundException`        | 404  | NotFoundError   |
| Duplicate room name / overlap   | `ConflictException`        | 409  | ConflictError   |
| Anything unexpected             | `Exception`                | 500  | InternalError   |

Response shape (always):
```json
{
  "error": "ValidationError",
  "message": "Booking duration must be at least 15 minutes"
}
```

**Why custom exception classes instead of Spring's `ResponseStatusException`?** Three reasons:
- Forces every error through the same handler, guaranteeing format consistency.
- Business rule names (`ValidationError`, `ConflictError`) are more meaningful than raw HTTP status text.
- The service layer throws domain exceptions without coupling to HTTP concepts.

---

## 4. Idempotency Implementation

### Why Idempotency Matters

Network retries, client timeouts, and load balancer replays can all cause the same booking request to arrive multiple times. Without idempotency, this creates duplicate bookings. The `Idempotency-Key` header lets the client signal "this is the same request" so the server returns the original result.

### Scoping Decision

Keys are scoped per `(idempotencyKey, organizerEmail)`. This means:

- Two different organizers can use the same key string without collision.
- A single organizer reusing a key always gets back their original booking.

**Why per-organizer instead of globally unique?** The spec suggests this for simplicity, and it maps well to real-world usage where each client generates their own UUIDs. Global uniqueness would require coordinating key generation across all clients, which is unnecessary overhead.

### Request Flow

```
Client sends POST /bookings with Idempotency-Key header
  │
  ▼
┌─────────────────────────────────────────┐
│ 1. Look up (key, organizer) in DB       │
│    Found? → Return cached booking (done)│
└────────────────┬────────────────────────┘
                 │ Not found
                 ▼
┌─────────────────────────────────────────┐
│ 2. Validate rules (time, duration, etc.)│
│ 3. Check overlaps                       │
│ 4. INSERT booking                       │
│ 5. INSERT idempotency_record            │──── On unique constraint violation ──┐
│ 6. COMMIT                               │                                     │
└────────────────┬────────────────────────┘                                     │
                 │                                                              │
                 ▼                                                              ▼
          Return new booking                                 Re-read existing record,
                                                             return cached booking
```

### Persistence

The `idempotency_records` table is a standard JPA entity stored in the same database as bookings. With the file-based H2 config (`jdbc:h2:file:./data/bookingdb`), records survive application restarts.

**Future consideration**: In production, idempotency records should have a TTL (e.g., 24–72 hours) with a scheduled cleanup job. The `created_at` column exists for this purpose but cleanup is not implemented in this version.

### Known Simplification: Key Reuse with Different Payload

If a client sends the same `Idempotency-Key` with a different request body (e.g., different room or time), the current implementation silently returns the original booking. It does **not** detect the payload mismatch.

A production-grade system (e.g., Stripe's approach) would:
1. Hash the request body and store it alongside the idempotency record.
2. On key reuse, compare the stored hash with the new request's hash.
3. If they differ, return `422 Unprocessable Entity` with a message like "Idempotency key already used with a different request."

This was omitted here because the spec scopes idempotency to "same key → same booking, no duplicates" without mentioning payload validation. Adding it would require a `request_hash` column on `IdempotencyRecord` and a SHA-256 computation on the serialized request body.

---

## 5. Concurrency Handling

### Two Concurrency Risks

**Risk 1: Duplicate idempotent bookings**

Two identical requests arrive simultaneously, both pass the "key not found" check, and both try to insert.

**Mitigation**: The `UNIQUE(idempotency_key, organizer_email)` DB constraint guarantees that only one insert succeeds. The loser gets a `DataIntegrityViolationException`, catches it, re-reads the winner's record, and returns it. This is a standard "insert-or-fetch" pattern.

```
Thread A                              Thread B
────────                              ────────
SELECT idempotency → not found        SELECT idempotency → not found
Validate → OK                         Validate → OK
Check overlap → OK                    Check overlap → OK
INSERT booking → OK                   INSERT booking → OK (different row)
INSERT idempotency → OK (wins)        INSERT idempotency → CONSTRAINT VIOLATION
COMMIT                                ROLLBACK → re-read → return A's booking
```

**Risk 2: Double-booking the same time slot**

Two bookings for different meetings but the same room/time arrive simultaneously, both pass the overlap check, and both insert.

**Mitigation (current)**: The overlap check and insert run within a single `@Transactional` boundary. Under H2's `READ_COMMITTED` isolation, Transaction B can read Transaction A's uncommitted rows in some edge cases, but there is a theoretical window where both could succeed.

**Mitigation (production recommendation)**: For PostgreSQL, add a pessimistic lock:

```sql
-- Lock the room row before checking overlaps
SELECT * FROM rooms WHERE id = :roomId FOR UPDATE;
-- Now check overlaps — no other transaction can book this room concurrently
SELECT COUNT(*) FROM bookings WHERE room_id = :roomId AND status = 'CONFIRMED'
                                AND start_time < :endTime AND end_time > :startTime;
```

This serializes all booking attempts for the same room, eliminating the race entirely. The lock is held only for the duration of the transaction (milliseconds), so throughput impact is minimal.

**Alternative considered**: `SERIALIZABLE` isolation level. This would catch the anomaly via serialization failure, but requires retry logic at the application layer and affects *all* queries in the transaction, not just the overlap check. `SELECT FOR UPDATE` is more surgical.

---

## 6. Cancellation Design

### Rules

1. **Grace period**: Cancellation is only allowed if the current time is more than 1 hour before `startTime`. This prevents last-minute cancellations that leave rooms unused.

2. **Idempotent cancel**: Cancelling an already-cancelled booking returns the existing cancelled booking (HTTP 200, no state change). This simplifies client retry logic.

3. **Freed slots**: Cancelled bookings are excluded from overlap checks (`WHERE status = 'CONFIRMED'`), so their time slot becomes immediately available for new bookings.

### Why POST instead of DELETE?

The spec uses `POST /bookings/{id}/cancel`. This is a deliberate choice: cancellation is a *state transition* (CONFIRMED → CANCELLED), not a resource deletion. The booking record persists for audit, reporting, and the idempotent-cancel requirement.

---

## 7. Utilization Calculation

### Formula

```
utilizationPercent = totalBookedHours / totalBusinessHours
```

Where:
- **Business hours** = Mon–Fri, 08:00–20:00 UTC (12 hours/day)
- **totalBusinessHours** = number of weekdays in `[from, to)` × 12, with partial-day clamping at boundaries
- **totalBookedHours** = sum of each CONFIRMED booking's duration within `[from, to]`, clamped to business hours only

**Timezone assumption**: The spec mentions "room's local time" for business-hour validation. This implementation assumes all rooms operate in UTC. For multi-timezone support, a `timezone VARCHAR` column would be added to the `Room` entity, and both the booking validation and utilization calculation would convert to the room's local zone before applying the 08:00–20:00 window. The current design isolates all timezone logic in `BookingService.validateWorkingHours()` and `UtilizationService.businessHoursBetween()`, so this change would be localized.

### Worked Example

**Scenario**: Report from Monday 2025-06-02 **10:00Z** to Wednesday 2025-06-04 **18:00Z**.

Total business hours: Mon 10:00–20:00 (10h) + Tue 08:00–20:00 (12h) + Wed 08:00–18:00 (10h) = **32h**.

Three confirmed bookings for Room Alpha:
- Booking A: Mon 14:00–16:00 (2h) — fully inside report range → **2h counted**
- Booking B: Mon 09:00–11:00 (2h) — starts before `from` (10:00), clamped to 10:00–11:00 → **1h counted** *(demonstrates `from`-clamping: the booking is valid but only the portion inside the report window contributes)*
- Booking C: Wed 17:00–19:00 (2h) — ends after `to` (18:00), clamped to 17:00–18:00 → **1h counted** *(demonstrates `to`-clamping)*

```
totalBookedHours  = 2 + 1 + 1 = 4h
totalBusinessHours = 10 + 12 + 10 = 32h
utilizationPercent = 4 / 32 = 0.125 (12.5%)
```

> All three bookings are valid under business rules (Mon–Fri, 08:00–20:00, ≤ 4h each). The example demonstrates both boundary clamps (`from` and `to`) alongside a fully-interior booking.

### Edge Cases

| Case                              | Handling                                                    |
|-----------------------------------|-------------------------------------------------------------|
| No bookings in range              | `totalBookedHours = 0`, `utilizationPercent = 0.0`          |
| Booking starts before `from`      | Clamped to `from` before counting                           |
| Booking ends after `to`           | Clamped to `to` before counting                             |
| Range is entirely a weekend       | `totalBusinessHours = 0`, `utilizationPercent = 0.0`        |
| `to` is exactly midnight          | That calendar day is excluded (avoids double-counting)      |
| Cancelled bookings in range       | Excluded — only `CONFIRMED` bookings contribute             |

### Implementation Detail

The calculation iterates day-by-day for both the denominator (total business hours) and the numerator (each booking's effective hours). While bookings are limited to 4 hours max, the day-by-day iteration is defensive — it correctly handles any future relaxation of the duration limit without code changes.