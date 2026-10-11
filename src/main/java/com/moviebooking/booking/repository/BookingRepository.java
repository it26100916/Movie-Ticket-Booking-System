package com.moviebooking.booking.repository;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Data access for bookings. */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Booking> findAllByOrderByCreatedAtDesc();

    boolean existsByCustomerId(Long customerId);

    boolean existsByShowId(Long showId);

    /**
     * Seats already held for a showtime by bookings in the given statuses.
     * excludeBookingId lets an edit ignore the booking being changed (pass -1 otherwise).
     */
    @Query("select seat from Booking b join b.seatCodes seat "
            + "where b.show.id = :showId and b.id <> :excludeBookingId and b.status in :statuses")
    List<String> findHeldSeatCodes(@Param("showId") Long showId,
                                   @Param("excludeBookingId") Long excludeBookingId,
                                   @Param("statuses") Collection<BookingStatus> statuses);
}
