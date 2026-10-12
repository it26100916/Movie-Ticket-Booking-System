package com.moviebooking.movie.controller;

import com.moviebooking.movie.dto.MovieDto.MovieRequest;
import com.moviebooking.movie.dto.MovieDto.MovieResponse;
import com.moviebooking.movie.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public movie browsing (READ) and admin CRUD under /api/admin/movies. */
@RestController
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/api/movies")
    public List<MovieResponse> search(@RequestParam(defaultValue = "") String title,
                                      @RequestParam(defaultValue = "") String genre) {
        return movieService.search(title, genre);
    }

    @GetMapping("/api/movies/{id}")
    public MovieResponse get(@PathVariable Long id) {
        return movieService.getById(id);
    }

    @PostMapping("/api/admin/movies")
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.create(request));
    }

    @PutMapping("/api/admin/movies/{id}")
    public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @DeleteMapping("/api/admin/movies/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        movieService.delete(id);
        return Map.of("message", "Movie deleted");
    }
}
