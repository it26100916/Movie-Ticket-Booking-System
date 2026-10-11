package com.moviebooking.theatre.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract screen (auditorium). Subclasses: StandardScreen, PremiumScreen, VipScreen.
 * <p>
 * INHERITANCE + POLYMORPHISM: each screen type defines its own category and price multiplier.
 * ABSTRACTION: callers use isValidSeat() and getAllSeatCodes() without knowing how seats are laid out.
 * ENCAPSULATION: layout values are changed only through setLayout(), which validates them.
 */
@Entity
@Table(name = "screens")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "screen_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "theatre_id", nullable = false)
    private Theatre theatre;

    @Column(nullable = false)
    private Integer rowCount;

    @Column(nullable = false)
    private Integer seatsPerRow;

    protected Screen() {
        // required by JPA
    }

    /** POLYMORPHISM: the screen type code stored in the database. */
    public abstract String getScreenType();

    /** POLYMORPHISM: human-readable category shown in the UI. */
    public abstract String getCategory();

    /** POLYMORPHISM: multiplier applied to the base ticket price. */
    public abstract BigDecimal getPriceMultiplier();

    /** Validates the layout: rows A..Z (max 26), seats per row 1..30. */
    public void setLayout(int rows, int seatsPerRow) {
        if (rows < 1 || rows > 26) {
            throw new IllegalArgumentException("Row count must be between 1 and 26");
        }
        if (seatsPerRow < 1 || seatsPerRow > 30) {
            throw new IllegalArgumentException("Seats per row must be between 1 and 30");
        }
        this.rowCount = rows;
        this.seatsPerRow = seatsPerRow;
    }

    public int getTotalSeats() {
        return rowCount * seatsPerRow;
    }

    /** ABSTRACTION: a seat code like "B7" is valid only if it exists in this layout. */
    public boolean isValidSeat(String seatCode) {
        if (seatCode == null || !seatCode.matches("^[A-Z]\\d{1,2}$")) {
            return false;
        }
        int rowIndex = seatCode.charAt(0) - 'A';
        int seatNumber = Integer.parseInt(seatCode.substring(1));
        return rowIndex < rowCount && seatNumber >= 1 && seatNumber <= seatsPerRow;
    }

    /** Builds every seat code in the layout: A1..A12, B1..B12, ... */
    public List<String> getAllSeatCodes() {
        List<String> codes = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            for (int s = 1; s <= seatsPerRow; s++) {
                codes.add((char) ('A' + r) + String.valueOf(s));
            }
        }
        return codes;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Theatre getTheatre() {
        return theatre;
    }

    public void setTheatre(Theatre theatre) {
        this.theatre = theatre;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public Integer getSeatsPerRow() {
        return seatsPerRow;
    }
}
