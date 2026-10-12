package com.moviebooking.booking.dto;

import com.moviebooking.booking.entity.Booking;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for bookings. */
public final class BookingDto {

    private BookingDto() {
    }

    public record BookingRequest(
            @NotNull Long showId,
            @NotEmpty List<String> seatCodes) {
    }

    public record BookingUpdateRequest(
            @NotEmpty List<String> seatCodes) {
    }

    public record BookingResponse(
            Long id,
            String reference,
            Long showId,
            String movieTitle,
            String theatreName,
            String screenName,
            LocalDateTime showTime,
            List<String> seatCodes,
            BigDecimal totalAmount,
            String status,
            String customerName,
            String customerEmail,
            LocalDateTime createdAt) {

        public static BookingResponse from(Booking b) {
            return new BookingResponse(
                    b.getId(), b.getReference(), b.getShow().getId(),
                    b.getShow().getMovie().getTitle(),
                    b.getShow().getScreen().getTheatre().getName(),
                    b.getShow().getScreen().getName(),
                    b.getShow().getStartTime(),
                    b.getSeatCodes().stream().sorted().toList(),
                    b.getTotalAmount(), b.getStatus().name(),
                    b.getCustomer().getFullName(), b.getCustomer().getEmail(),
                    b.getCreatedAt());
        }
    }

    /** Result of a cancellation: what was refunded and under which policy. */
    public record CancellationResult(
            String message,
            String policy,
            BigDecimal refundAmount,
            String bookingStatus) {
    }
}
