package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class RateLimitFilter implements Filter {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN_CLIENT = "unknown-client";
    private static final long MINIMUM_RETRY_AFTER_SECONDS = 1L;
    private static final List<String> EXEMPT_PATH_PREFIXES =
            List.of("/actuator", "/swagger-ui", "/v3/api-docs", "/webjars");

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = Objects.requireNonNull(rateLimitService, "rateLimitService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        if (isExempt(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        var verdict = rateLimitService.tryConsume(clientIdentifierOf(httpRequest));
        if (verdict.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        rejectWithTooManyRequests((HttpServletResponse) response, verdict.retryAfter());
    }

    private boolean isExempt(String requestUri) {
        return EXEMPT_PATH_PREFIXES.stream().anyMatch(requestUri::startsWith);
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, Duration retryAfter) throws IOException {
        var retryAfterSeconds = Math.max(retryAfter.toSeconds(), MINIMUM_RETRY_AFTER_SECONDS);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        var apiError = ApiError.of(ErrorCode.RATE_LIMIT_EXCEEDED,
                "request budget exhausted, retry after " + retryAfterSeconds + " seconds",
                CorrelationId.current(), Instant.now());
        objectMapper.writeValue(response.getOutputStream(), apiError);
    }

    private String clientIdentifierOf(HttpServletRequest request) {
        var forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].strip();
        }
        var remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? UNKNOWN_CLIENT : remoteAddress;
    }
}
