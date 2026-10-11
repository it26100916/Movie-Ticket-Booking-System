package com.moviebooking.booking.entity;

import java.util.List;

/** Lifecycle of a booking: PENDING_PAYMENT -> CONFIRMED, or -> CANCELLED at any time after booking. */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED;

    /** Statuses that hold seats (a cancelled booking releases its seats). */
    public static List<BookingStatus> activeStatuses() {
        return List.of(PENDING_PAYMENT, CONFIRMED);
    }
}
