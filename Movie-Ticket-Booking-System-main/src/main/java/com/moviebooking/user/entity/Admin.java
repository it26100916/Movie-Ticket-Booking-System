package com.moviebooking.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * An administrator who manages movies, theatres, shows, bookings and reports.
 * INHERITANCE: extends User.
 * ABSTRACTION: callers ask canManageAdmins() instead of comparing permission strings.
 */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String STAFF = "STAFF";

    @Column(name = "permission_level", length = 30)
    private String permissionLevel;

    protected Admin() {
        // required by JPA
    }

    public Admin(String fullName, String email, String passwordHash, String phone, String permissionLevel) {
        super(fullName, email, passwordHash, phone);
        setPermissionLevel(permissionLevel);
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    /** INFORMATION HIDING / VALIDATION: only the two known levels are accepted. */
    public void setPermissionLevel(String permissionLevel) {
        if (!SUPER_ADMIN.equals(permissionLevel) && !STAFF.equals(permissionLevel)) {
            throw new IllegalArgumentException("Permission level must be SUPER_ADMIN or STAFF");
        }
        this.permissionLevel = permissionLevel;
    }

    public boolean canManageAdmins() {
        return SUPER_ADMIN.equals(permissionLevel);
    }
}
