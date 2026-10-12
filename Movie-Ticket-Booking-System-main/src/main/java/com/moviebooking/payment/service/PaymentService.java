package com.moviebooking.payment.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.exception.ResourceNotFoundException;
import com.moviebooking.payment.dto.PaymentDto.PaymentRequest;
import com.moviebooking.payment.dto.PaymentDto.PaymentResponse;
import com.moviebooking.payment.dto.PaymentDto.PaymentStatusRequest;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.entity.PaymentMethodType;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.processor.PaymentCharge;
import com.moviebooking.payment.processor.PaymentProcessor;
import com.moviebooking.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payment CRUD and checkout.
 * CREATE: pay()   READ: listMine() / listAll()   UPDATE: updateStatus()   DELETE: delete()
 */
@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final List<PaymentProcessor> processors;

    public PaymentService(PaymentRepository payments, BookingRepository bookings,
                          List<PaymentProcessor> processors) {
        this.payments = payments;
        this.bookings = bookings;
        this.processors = processors;
    }

    /**
     * CREATE: pay for a booking awaiting payment.
     * Success confirms the booking and awards loyalty points (1 per seat).
     */
    public PaymentResponse pay(Long customerId, PaymentRequest req) {
        Booking booking = bookings.findById(req.bookingId())
                .filter(b -> b.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + req.bookingId()));
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("This booking is not awaiting payment");
        }

        // POLYMORPHISM: the matching processor validates and charges in its own way.
        PaymentProcessor processor = processorFor(req.method());
        PaymentCharge charge = processor.charge(booking.getTotalAmount(), req.reference());

        Payment payment = new Payment(charge.transactionRef(), booking, charge.totalAmount(),
                charge.convenienceFee(), req.method(), PaymentStatus.SUCCESS, charge.maskedReference());
        payments.save(payment);

        booking.markConfirmed();
        booking.getCustomer().addLoyaltyPoints(booking.getSeatCount());
        return PaymentResponse.from(payment, processor.describe());
    }

    /** READ: the customer's own payment history. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listMine(Long customerId) {
        return payments.findByBookingCustomerIdOrderByPaidAtDesc(customerId).stream().map(this::toResponse).toList();
    }

    /** READ (admin): all payments. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listAll() {
        return payments.findAllByOrderByPaidAtDesc().stream().map(this::toResponse).toList();
    }

    /**
     * UPDATE (admin): record a manual refund (e.g. cash returned at the counter).
     * Only REFUNDED is accepted here; automatic refunds happen during cancellation.
     */
    public PaymentResponse updateStatus(Long id, PaymentStatusRequest req) {
        Payment payment = find(id);
        if (req.status() != PaymentStatus.REFUNDED) {
            throw new BusinessException("Only a manual full refund (REFUNDED) can be recorded here");
        }
        Booking booking = payment.getBooking();
        BigDecimal remaining = booking.getTotalAmount().subtract(payment.getRefundedAmount());
        if (remaining.signum() <= 0) {
            throw new BusinessException("This payment is already fully refunded");
        }
        payment.refund(remaining, booking.getTotalAmount());
        booking.markCancelled();
        return toResponse(payment);
    }

    /** DELETE (admin): remove a fully refunded payment from the ledger. */
    public void delete(Long id) {
        Payment payment = find(id);
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new BusinessException("Only fully refunded payments can be deleted");
        }
        payments.delete(payment);
    }

    private PaymentProcessor processorFor(PaymentMethodType method) {
        return processors.stream()
                .filter(p -> p.method() == method)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unsupported payment method: " + method));
    }

    private Payment find(Long id) {
        return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment, processorFor(payment.getMethod()).describe());
    }
}
