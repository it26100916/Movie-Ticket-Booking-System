package com.moviebooking.show.repository;

import com.moviebooking.show.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Data access for showtimes. */
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    boolean existsByMovieId(Long movieId);

    boolean existsByScreenId(Long screenId);

    /**
     * Counts showtimes on the same screen that overlap [start, end).
     * excludeId lets an update ignore the showtime being edited (pass -1 for new shows).
     */
    @Query("select count(s) from Showtime s where s.screen.id = :screenId and s.id <> :excludeId "
            + "and s.startTime < :end and s.endTime > :start")
    long countOverlapping(@Param("screenId") Long screenId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("excludeId") Long excludeId);

    @Query("select s from Showtime s where s.startTime >= :from and s.startTime < :to order by s.startTime asc")
    List<Showtime> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select s from Showtime s where s.movie.id = :movieId and s.startTime >= :from "
            + "and s.startTime < :to order by s.startTime asc")
    List<Showtime> findInRangeForMovie(@Param("movieId") Long movieId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
