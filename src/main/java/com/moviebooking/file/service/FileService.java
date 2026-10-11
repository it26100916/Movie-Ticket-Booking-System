package com.moviebooking.file.service;

import com.moviebooking.booking.entity.Booking;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.exception.BusinessException;
import com.moviebooking.file.dto.FileDto.ImportResult;
import com.moviebooking.movie.dto.MovieDto.MovieRequest;
import com.moviebooking.movie.service.MovieService;
import com.moviebooking.payment.entity.Payment;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.user.dto.AuthDto.RegisterCustomerRequest;
import com.moviebooking.user.dto.UserDto.CreateAdminRequest;
import com.moviebooking.user.service.AdminService;
import com.moviebooking.user.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FILE HANDLING (text files):
 * - READ  : importMovies() reads data/seed/movies.txt, importUsers() reads data/seed/users.txt
 * - WRITE : exportSalesReport() writes a timestamped report to data/reports/
 * <p>
 * File format (pipe separated, '#' starts a comment line):
 *   movies.txt : title|genre|language|durationMinutes|rating|format|releaseDate|director|producers|writers|cast|trailerUrl|stillUrls|description
 *   users.txt  : type(CUSTOMER|ADMIN)|fullName|email|password|phone|permissionLevel
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MovieService movieService;
    private final CustomerService customerService;
    private final AdminService adminService;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final Path seedDir;
    private final Path reportsDir;

    public FileService(MovieService movieService, CustomerService customerService, AdminService adminService,
                       BookingRepository bookings, PaymentRepository payments,
                       @Value("${app.data.seed-dir}") String seedDir,
                       @Value("${app.data.reports-dir}") String reportsDir) {
        this.movieService = movieService;
        this.customerService = customerService;
        this.adminService = adminService;
        this.bookings = bookings;
        this.payments = payments;
        Path configuredSeed = Paths.get(seedDir);
        this.seedDir = locateSeedDir(configuredSeed);
        // Reports go next to the seed folder (data/reports) when the data folder was found by searching.
        this.reportsDir = this.seedDir.equals(configuredSeed)
                ? Paths.get(reportsDir)
                : this.seedDir.resolveSibling(Paths.get(reportsDir).getFileName());
        log.info("Sample data folder: {}", this.seedDir.toAbsolutePath());
    }

    /**
     * Finds the seed folder even when the app is started from another working directory
     * (for example "java -jar target/app.jar" from inside target/, or from an IDE).
     * It first tries the configured relative path, then walks up from the jar/class location.
     */
    private static Path locateSeedDir(Path configured) {
        if (Files.isDirectory(configured)) {
            return configured;
        }
        try {
            // Inside a Spring Boot fat jar the code location is ".../app.jar/!BOOT-INF/classes/" (or "jar:file:...!/...").
            // Keep only the jar file path, which is everything before the first '!'.
            String location = FileService.class.getProtectionDomain().getCodeSource().getLocation().toString();
            // Remove URL prefixes such as "nested:", "jar:" and "file:" and anything after the jar name.
            location = location.replaceFirst("^(nested:|jar:|file:)+", "");
            if (location.indexOf('!') >= 0) {
                location = location.substring(0, location.indexOf('!'));
            }
            while (location.length() > 1 && location.endsWith("/")) {
                location = location.substring(0, location.length() - 1);
            }
            // The location is URL-encoded ("movie%20ticket") and on Windows looks like "/C:/Users/...".
            location = URLDecoder.decode(location.replace("+", "%2B"), StandardCharsets.UTF_8);
            if (location.matches("^/[A-Za-z]:.*")) {
                location = location.substring(1);
            }
            Path start = Paths.get(location);
            Path dir = Files.isDirectory(start) ? start : start.getParent();
            for (int up = 0; dir != null && up < 5; up++, dir = dir.getParent()) {
                Path candidate = dir.resolve(configured);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception e) {
            log.warn("Could not search for data/seed from the application location: {}", e.toString());
        }
        return configured;
    }

    /** READ a text file and create the movies it lists (duplicates are skipped). */
    public ImportResult importMovies() {
        List<String> lines = readLines("movies.txt");
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                // The description is the last field, so it may safely contain '|' characters.
                String[] f = line.split("\\|", 14);
                if (f.length < 14) {
                    throw new IllegalArgumentException("expected 14 fields separated by |");
                }
                if (movieService.titleExists(f[0])) {
                    skipped++;
                    continue;
                }
                MovieRequest req = new MovieRequest(
                        f[0].trim(), f[1].trim(), f[2].trim(),
                        Integer.parseInt(f[3].trim()),
                        f[4].trim(),
                        f[13].trim(),
                        null,
                        f[6].isBlank() ? null : LocalDate.parse(f[6].trim()),
                        f[5].trim(),
                        f[7].trim(),
                        f[8].trim(),
                        f[9].trim(),
                        f[10].trim(),
                        f[11].trim(),
                        f[12].trim());
                movieService.create(req);
                imported++;
            } catch (RuntimeException ex) {
                errors.add("Line " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new ImportResult("movies.txt", imported, skipped, errors);
    }

    /** READ a text file and create the customer and admin accounts it lists. */
    public ImportResult importUsers() {
        List<String> lines = readLines("users.txt");
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                String[] f = line.split("\\|", -1);
                if (f.length < 5) {
                    throw new IllegalArgumentException("expected at least 5 fields");
                }
                String type = f[0].trim().toUpperCase();
                if ("CUSTOMER".equals(type)) {
                    customerService.register(new RegisterCustomerRequest(f[1], f[2], f[3], f[4]));
                } else if ("ADMIN".equals(type)) {
                    String level = f.length > 5 ? f[5] : null;
                    adminService.createAdmin(null, new CreateAdminRequest(f[1], f[2], f[3], level));
                } else {
                    throw new IllegalArgumentException("unknown type " + type);
                }
                imported++;
            } catch (BusinessException ex) {
                skipped++; // usually "email already exists"
            } catch (RuntimeException ex) {
                errors.add("Line " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new ImportResult("users.txt", imported, skipped, errors);
    }

    /** WRITE a sales report to data/reports/sales-report-<timestamp>.txt and return its path. */
    public Path exportSalesReport() {
        List<Booking> allBookings = bookings.findAllByOrderByCreatedAtDesc();
        List<Payment> allPayments = payments.findAllByOrderByPaidAtDesc();

        BigDecimal gross = allPayments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunds = allPayments.stream().map(Payment::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
        long confirmed = countStatus(allBookings, BookingStatus.CONFIRMED);
        long pending = countStatus(allBookings, BookingStatus.PENDING_PAYMENT);
        long cancelled = countStatus(allBookings, BookingStatus.CANCELLED);

        Map<String, Long> seatsPerMovie = allBookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getShow().getMovie().getTitle(), TreeMap::new,
                        Collectors.summingLong(Booking::getSeatCount)));

        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(96);
        sb.append(line).append('\n');
        sb.append("MOVIE TICKET BOOKING - SALES REPORT\n");
        sb.append("Generated: ").append(LocalDateTime.now()).append('\n');
        sb.append(line).append('\n');
        sb.append(String.format("Bookings  : %d total | %d confirmed | %d awaiting payment | %d cancelled%n",
                allBookings.size(), confirmed, pending, cancelled));
        sb.append(String.format("Payments  : %d%n", allPayments.size()));
        sb.append(String.format("Gross     : LKR %,.2f%n", gross));
        sb.append(String.format("Refunds   : LKR %,.2f%n", refunds));
        sb.append(String.format("Net sales : LKR %,.2f%n", net));
        sb.append('\n').append("Seats sold per movie (excluding cancelled):\n");
        seatsPerMovie.forEach((title, seats) -> sb.append(String.format("  %-30s %6d%n", title, seats)));

        sb.append('\n').append("Booking details:\n");
        sb.append(String.format("%-10s %-28s %-16s %-17s %6s %14s  %-22s%n",
                "REF", "MOVIE", "CUSTOMER", "SHOW TIME", "SEATS", "AMOUNT", "STATUS"));
        for (Booking b : allBookings) {
            sb.append(String.format("%-10s %-28s %-16s %-17s %6d %14s  %-22s%n",
                    b.getReference(),
                    trim(b.getShow().getMovie().getTitle(), 28),
                    trim(b.getCustomer().getFullName(), 16),
                    b.getShow().getStartTime().toLocalDate() + " " + b.getShow().getStartTime().toLocalTime().withSecond(0).toString(),
                    b.getSeatCount(),
                    String.format("LKR %,.2f", b.getTotalAmount()),
                    b.getStatus().name()));
        }
        sb.append(line).append('\n');

        try {
            Files.createDirectories(reportsDir);
            Path file = reportsDir.resolve("sales-report-" + LocalDateTime.now().format(STAMP) + ".txt");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write report", e);
        }
    }

    private List<String> readLines(String fileName) {
        Path path = seedDir.resolve(fileName);
        if (!Files.exists(path)) {
            throw new BusinessException("Seed file not found: " + path.toAbsolutePath());
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("Could not read " + fileName + ": " + e.getMessage());
        }
    }

    private static long countStatus(List<Booking> list, BookingStatus status) {
        return list.stream().filter(b -> b.getStatus() == status).count();
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "~";
    }
}
