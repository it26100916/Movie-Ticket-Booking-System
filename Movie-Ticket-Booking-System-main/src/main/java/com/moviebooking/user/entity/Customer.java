package com.moviebooking.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A customer who browses movies and books tickets.
 * INHERITANCE: extends User and adds loyalty points.
 */
@Entity
@DiscriminatorValue("CUSTOMER")
public class Customer extends User {

    @Column(name = "loyalty_points")
    private Integer loyaltyPoints;

    protected Customer() {
        // required by JPA
    }

    public Customer(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone);
        this.loyaltyPoints = 0;
    }

    @Override
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints == null ? 0 : loyaltyPoints;
    }

    /** Business rule: customers earn 1 loyalty point per seat when a payment succeeds. */
    public void addLoyaltyPoints(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Loyalty points cannot be negative");
        }
        this.loyaltyPoints = getLoyaltyPoints() + points;
    }
}
