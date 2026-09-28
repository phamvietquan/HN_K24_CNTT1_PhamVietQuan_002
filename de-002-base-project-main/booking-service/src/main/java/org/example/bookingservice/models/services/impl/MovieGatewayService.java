package org.example.bookingservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bookingservice.clients.MovieClient;
import org.example.bookingservice.exceptions.MovieNotFoundException;
import org.example.bookingservice.exceptions.MovieServiceException;
import org.example.bookingservice.models.dto.responses.MovieResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieGatewayService {

    private final MovieClient movieClient;

    @CircuitBreaker(name = "movieService", fallbackMethod = "getMovieFallback")
    public MovieResponse getMovieById(Long movieId) {
        MovieResponse movie = movieClient.getMovieById(movieId);

        if (movie == null || movie.id() == null || movie.ticketPrice() == null) {
            throw new MovieServiceException(
                    "Movie service returned invalid data for movie " + movieId
            );
        }
        if (movie.ticketPrice() < 0) {
            throw new MovieServiceException(
                    "Movie ticket price must not be negative for movie " + movieId
            );
        }

        return movie;
    }

    public MovieResponse getMovieFallback(Long movieId, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for movieId: {}, error: {}", movieId, throwable != null ? throwable.getMessage() : "unknown");

        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof FeignException.NotFound || (cause instanceof FeignException fe && fe.status() == 404)) {
                throw new MovieNotFoundException(movieId);
            }
            if (cause instanceof MovieNotFoundException movieNotFoundException) {
                throw movieNotFoundException;
            }
            cause = cause.getCause();
        }

        if (throwable instanceof MovieServiceException movieServiceException) {
            throw movieServiceException;
        }

        throw new MovieServiceException(
                "Movie service is unavailable for movie " + movieId,
                throwable
        );
    }
}
