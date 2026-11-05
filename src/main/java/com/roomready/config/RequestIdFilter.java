package com.roomready.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id, puts it in the logging context (MDC) and in the response header,
 * so a line on the console and the answer the caller got back can be matched up.
 * Every request also leaves one log line on stdout - method, path, status and id; never a body.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger("com.roomready.request");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString().substring(0, 12);
        MDC.put(KEY, id);
        response.setHeader("X-Request-Id", id);
        try {
            chain.doFilter(request, response);
            log.info("request method={} path={} status={} requestId={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), id);
        } finally {
            MDC.remove(KEY);
        }
    }
}