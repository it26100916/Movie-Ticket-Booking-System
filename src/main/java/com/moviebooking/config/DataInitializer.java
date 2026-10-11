package com.moviebooking.config;

import com.moviebooking.file.service.FileService;
import com.moviebooking.movie.dto.MovieDto.MovieResponse;
import com.moviebooking.movie.service.MovieService;
import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.show.service.ShowService;
import com.moviebooking.show.repository.ShowtimeRepository;
import com.moviebooking.theatre.dto.ScreenDto.ScreenRequest;
import com.moviebooking.theatre.dto.ScreenDto.ScreenResponse;
import com.moviebooking.theatre.dto.TheatreDto.TheatreRequest;
import com.moviebooking.theatre.dto.TheatreDto.TheatreResponse;
import com.moviebooking.theatre.repository.TheatreRepository;
import com.moviebooking.theatre.service.ScreenService;
import com.moviebooking.theatre.service.TheatreService;
import com.moviebooking.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Loads demo data on the first start (only when tables are empty):
 * users and movies come from the text files in data/seed, theatres/screens/showtimes are created in code.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FileService fileService;
    private final UserRepository users;
    private final TheatreRepository theatres;
    private final ShowtimeRepository showtimes;
    private final MovieService movieService;
    private final TheatreService theatreService;
    private final ScreenService screenService;
    private final ShowService showService;

    public DataInitializer(FileService fileService, UserRepository users, TheatreRepository theatres,
                           ShowtimeRepository showtimes, MovieService movieService,
                           TheatreService theatreService, ScreenService screenService, ShowService showService) {
        this.fileService = fileService;
        this.users = users;
        this.theatres = theatres;
        this.showtimes = showtimes;
        this.movieService = movieService;
        this.theatreService = theatreService;
        this.screenService = screenService;
        this.showService = showService;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            importSafely("Users", fileService::importUsers);
        }
        if (movieService.search("", "").isEmpty()) {
            importSafely("Movies", fileService::importMovies);
        }
        if (theatres.count() == 0) {
            seedCinemas();
        }
        if (showtimes.count() == 0) {
            seedShowtimes();
        }
    }

    /**
     * A missing or unreadable sample-data file must not stop the application from starting.
     * The problem is logged with the folder that was searched, and the app runs without that sample data.
     */
    private void importSafely(String label, Supplier<?> importAction) {
        try {
            log.info("{} import: {}", label, importAction.get());
        } catch (RuntimeException e) {
            log.error("{} sample data was NOT imported: {}. Check that the data/seed folder exists next to the project "
                    + "(see README troubleshooting). The application continues to start.", label, e.getMessage());
        }
    }

    private void seedCinemas() {
        TheatreResponse negombo = theatreService.create(new TheatreRequest(
                "CineMax Negombo", "Negombo", "Main Street, Negombo", "031-2000000"));
        theatreService.create(new TheatreRequest(
                "CineMax Colombo", "Colombo", "Galle Road, Colombo 03", "011-2000000"));

        screenService.create(new ScreenRequest("Screen 1 - Standard", negombo.id(), "STANDARD", 8, 12));
        screenService.create(new ScreenRequest("Screen 2 - Premium", negombo.id(), "PREMIUM", 6, 10));
        screenService.create(new ScreenRequest("Screen 3 - VIP", negombo.id(), "VIP", 4, 8));
    }

    /** Three days of shows starting tomorrow, spread over three screens and two time slots. */
    private void seedShowtimes() {
        List<MovieResponse> allMovies = movieService.search("", "");
        TheatreResponse theatre = theatreService.list().stream()
                .filter(t -> t.name().equals("CineMax Negombo")).findFirst().orElse(null);
        if (theatre == null) {
            return;
        }
        List<ScreenResponse> screens = screenService.list(theatre.id());
        LocalTime[] slots = {LocalTime.of(11, 0), LocalTime.of(15, 0)};
        BigDecimal basePrice = new BigDecimal("1500.00");

        for (int day = 1; day <= 3; day++) {
            LocalDate date = LocalDate.now().plusDays(day);
            for (int j = 0; j < allMovies.size(); j++) {
                MovieResponse movie = allMovies.get(j);
                ScreenResponse screen = screens.get(j % screens.size());
                LocalTime slot = slots[(j / screens.size()) % slots.length];
                try {
                    showService.create(new ShowRequest(movie.id(), screen.id(),
                            LocalDateTime.of(date, slot), basePrice));
                } catch (RuntimeException ex) {
                    log.warn("Skipped seed showtime for {}: {}", movie.title(), ex.getMessage());
                }
            }
        }
        log.info("Seeded showtimes for {} days", 3);
    }
}
