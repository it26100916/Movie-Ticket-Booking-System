package com.moviebooking.show.dto;

import com.moviebooking.show.entity.Showtime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for showtimes. */
public final class ShowDto {

    private ShowDto() {
    }

    public record ShowRequest(
            @NotNull Long movieId,
            @NotNull Long screenId,
            @NotNull LocalDateTime startTime,
            @NotNull @DecimalMin("1.00") BigDecimal basePrice) {
    }

    /** Showtime with live seat information: the price the customer pays and which seats are taken. */
    public record ShowResponse(
            Long id,
            Long movieId,
            String movieTitle,
            String movieFormat,
            Long screenId,
            String screenName,
            String theatreName,
            String screenType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice,
            BigDecimal seatPrice,
            String pricingStrategy,
            Integer rowCount,
            Integer seatsPerRow,
            Integer totalSeats,
            List<String> bookedSeats,
            Integer availableSeats) {

        public static ShowResponse from(Showtime s, BigDecimal seatPrice, String pricingName,
                                        List<String> bookedSeats) {
            int total = s.getScreen().getTotalSeats();
            return new ShowResponse(
                    s.getId(), s.getMovie().getId(), s.getMovie().getTitle(), s.getMovie().getFormat(),
                    s.getScreen().getId(), s.getScreen().getName(), s.getScreen().getTheatre().getName(),
                    s.getScreen().getScreenType(), s.getStartTime(), s.getEndTime(), s.getBasePrice(),
                    seatPrice, pricingName, s.getScreen().getRowCount(), s.getScreen().getSeatsPerRow(),
                    total, bookedSeats, total - bookedSeats.size());
        }
    }
}
