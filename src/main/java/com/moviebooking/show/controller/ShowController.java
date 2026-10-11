package com.moviebooking.show.controller;

import com.moviebooking.show.dto.ShowDto.ShowRequest;
import com.moviebooking.show.dto.ShowDto.ShowResponse;
import com.moviebooking.show.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Public showtime search (READ) and admin CRUD under /api/admin/shows. */
@RestController
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/api/shows")
    public List<ShowResponse> search(@RequestParam(required = false) Long movieId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showService.search(movieId, date);
    }

    @GetMapping("/api/shows/{id}")
    public ShowResponse get(@PathVariable Long id) {
        return showService.getById(id);
    }

    @GetMapping("/api/admin/shows")
    public List<ShowResponse> listAll() {
        return showService.listAll();
    }

    @PostMapping("/api/admin/shows")
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showService.create(request));
    }

    @PutMapping("/api/admin/shows/{id}")
    public ShowResponse update(@PathVariable Long id, @Valid @RequestBody ShowRequest request) {
        return showService.update(id, request);
    }

    @DeleteMapping("/api/admin/shows/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        showService.delete(id);
        return Map.of("message", "Showtime deleted");
    }
}
