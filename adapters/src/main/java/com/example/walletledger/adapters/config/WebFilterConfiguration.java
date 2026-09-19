package com.example.walletledger.adapters.config;

import com.example.walletledger.adapters.web.CorrelationIdFilter;
import com.example.walletledger.adapters.web.RateLimitFilter;
import com.example.walletledger.adapters.web.RateLimitService;
import com.example.walletledger.adapters.web.SecurityHeadersFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebFilterConfiguration {

    private static final String ALL_REQUESTS = "/*";
    private static final int CORRELATION_ID_FILTER_ORDER = 10;
    private static final int SECURITY_HEADERS_FILTER_ORDER = 20;
    private static final int RATE_LIMIT_FILTER_ORDER = 30;

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        return registrationOf(new CorrelationIdFilter(), CORRELATION_ID_FILTER_ORDER);
    }

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        return registrationOf(new SecurityHeadersFilter(), SECURITY_HEADERS_FILTER_ORDER);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitService rateLimitService,
                                                                               ObjectMapper objectMapper) {
        return registrationOf(new RateLimitFilter(rateLimitService, objectMapper), RATE_LIMIT_FILTER_ORDER);
    }

    private <T extends Filter> FilterRegistrationBean<T> registrationOf(T filter, int order) {
        var registration = new FilterRegistrationBean<T>(filter);
        registration.addUrlPatterns(ALL_REQUESTS);
        registration.setOrder(order);
        return registration;
    }
}
