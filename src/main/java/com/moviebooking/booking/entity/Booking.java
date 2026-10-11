package com.moviebooking.booking.entity;

import com.moviebooking.show.entity.Showtime;
import com.moviebooking.user.entity.Customer;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A customer's reservation of one or more seats for one showtime.
 * ENCAPSULATION: the seat set is exposed read-only; changes go through replaceSeats() and the status methods.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Showtime show;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_code", nullable = false, length = 6)
    private Set<String> seatCodes = new HashSet<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Booking() {
        // required by JPA
    }

    public Booking(String reference, Customer customer, Showtime show, Set<String> seats, BigDecimal totalAmount) {
        this.reference = reference;
        this.customer = customer;
        this.show = show;
        this.seatCodes = new HashSet<>(seats);
        this.totalAmount = totalAmount;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = LocalDateTime.now();
    }

    /** Replaces the seats (only used while the booking is still awaiting payment). */
    public void replaceSeats(Set<String> seats, BigDecimal newTotal) {
        seatCodes.clear();
        seatCodes.addAll(seats);
        this.totalAmount = newTotal;
    }

    public void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void markCancelled() {
        this.status = BookingStatus.CANCELLED;
    }

    public int getSeatCount() {
        return seatCodes.size();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Showtime getShow() {
        return show;
    }

    /** Read-only view (information hiding). */
    public Set<String> getSeatCodes() {
        return Collections.unmodifiableSet(seatCodes);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
