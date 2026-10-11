package com.moviebooking.booking.service;

import com.moviebooking.booking.dto.BookingDto.BookingRequest;
import com.moviebooking.booking.dto.BookingDto.BookingResponse;
import com.moviebooking.booking.dto.BookingDto.BookingUpdateRequest;
import com.moviebooking.booking.dto.BookingDto.CancellationResult;
import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.policy.CancellationPolicy;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.show.entity.Showtime;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.user.entity.Customer;
import com.moviebooking.user.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Booking CRUD and seat reservation.
 * CREATE: create()   READ: listMine() / getMine() / listAll()   UPDATE: updateSeats()   DELETE: cancel()
 */
@Service
@Transactional
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 10;

    private final BookingRepository bookings;
    private final ShowtimeRepository showtimes;
    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final ShowService showService;
    private final List<CancellationPolicy> policies;

    public BookingService(BookingRepository bookings, ShowtimeRepository showtimes, CustomerRepository customers,
                          PaymentRepository payments, ShowService showService,
                          List<CancellationPolicy> policies) {
        this.bookings = bookings;
        this.showtimes = showtimes;
        this.customers = customers;
        this.payments = payments;
        this.showService = showService;
        this.policies = policies;
    }

    /** CREATE: reserve seats. The booking waits for payment (PENDING_PAYMENT). */
    public BookingResponse create(Long customerId, BookingRequest req) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        Showtime show = findShow(req.showId());
        if (!show.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("This showtime has already started");
        }
        Set<String> seats = validateSeats(show, req.seatCodes(), -1L);
        BigDecimal total = totalFor(show, seats.size());
        Booking booking = new Booking(newReference(), customer, show, seats, total);
        return BookingResponse.from(bookings.save(booking));
    }

    /** READ: the logged-in customer's bookings, newest first. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(Long customerId) {
        return bookings.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(BookingResponse::from).toList();
    }

    /** READ: one booking that belongs to this customer. */
    @Transactional(readOnly = true)
    public BookingResponse getMine(Long customerId, Long bookingId) {
        return BookingResponse.from(findOwned(customerId, bookingId));
    }

    /** READ (admin): all bookings. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listAll() {
        return bookings.findAllByOrderByCreatedAtDesc().stream().map(BookingResponse::from).toList();
    }

    /** UPDATE: change seats while the booking is still awaiting payment. */
    public BookingResponse updateSeats(Long customerId, Long bookingId, BookingUpdateRequest req) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("Only bookings awaiting payment can change seats");
        }
        Set<String> seats = validateSeats(booking.getShow(), req.seatCodes(), bookingId);
        booking.replaceSeats(seats, totalFor(booking.getShow(), seats.size()));
        return BookingResponse.from(booking);
    }

    /**
     * DELETE (cancel):
     * - awaiting payment -> the booking row is deleted, seats are released;
     * - confirmed -> cancellation policy decides the refund, payment is marked refunded.
     */
    public CancellationResult cancel(Long customerId, Long bookingId) {
        Booking booking = findOwned(customerId, bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException("This booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            bookings.delete(booking);
            return new CancellationResult("Reservation removed and seats released",
                    "No payment taken", BigDecimal.ZERO.setScale(2), "DELETED");
        }

        long hours = Duration.between(LocalDateTime.now(), booking.getShow().getStartTime()).toHours();
        CancellationPolicy policy = resolvePolicy(hours);
        BigDecimal refund = policy.refundFor(booking.getTotalAmount());
        if (refund.signum() > 0) {
            for (Payment payment : payments.findByBookingId(bookingId)) {
                payment.refund(refund, booking.getTotalAmount());
            }
        }
        booking.markCancelled();
        return new CancellationResult("Booking cancelled", policy.name(), refund,
                booking.getStatus().name());
    }

    /** DELETE (admin): remove a cancelled booking together with its payment records. */
    public void adminDelete(Long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new BusinessException("Only cancelled bookings can be deleted");
        }
        payments.deleteAll(payments.findByBookingId(bookingId));
        bookings.delete(booking);
    }

    /**
     * Validates requested seats: format, layout, duplicates and clashes with other bookings.
     * Returns normalised seat codes (upper case).
     */
    private Set<String> validateSeats(Showtime show, List<String> rawSeats, Long excludeBookingId) {
        if (rawSeats == null || rawSeats.isEmpty()) {
            throw new BusinessException("Select at least one seat");
        }
        if (rawSeats.size() > MAX_SEATS_PER_BOOKING) {
            throw new BusinessException("You can book at most " + MAX_SEATS_PER_BOOKING + " seats per booking");
        }
        Set<String> seats = new LinkedHashSet<>();
        for (String raw : rawSeats) {
            String code = raw == null ? "" : raw.trim().toUpperCase();
            // ABSTRACTION: the screen decides whether a seat exists in its layout.
            if (!show.getScreen().isValidSeat(code)) {
                throw new BusinessException("Invalid seat: " + raw);
            }
            if (!seats.add(code)) {
                throw new BusinessException("Seat " + code + " was selected twice");
            }
        }
        Set<String> taken = new HashSet<>(bookings.findHeldSeatCodes(show.getId(),
                excludeBookingId == null ? -1L : excludeBookingId, BookingStatus.activeStatuses()));
        List<String> clashes = seats.stream().filter(taken::contains).sorted().toList();
        if (!clashes.isEmpty()) {
            throw new BusinessException("Seat(s) already taken: " + String.join(", ", clashes));
        }
        return seats;
    }

    private BigDecimal totalFor(Showtime show, int seatCount) {
        return showService.seatPriceFor(show).multiply(BigDecimal.valueOf(seatCount))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CancellationPolicy resolvePolicy(long hoursBeforeShow) {
        return policies.stream()
                .filter(p -> p.supports(hoursBeforeShow))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No cancellation policy matches"));
    }

    private Booking findOwned(Long customerId, Long bookingId) {
        return bookings.findById(bookingId)
                .filter(b -> b.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    private Showtime findShow(Long id) {
        return showtimes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + id));
    }

    private static String newReference() {
        return "BK" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
