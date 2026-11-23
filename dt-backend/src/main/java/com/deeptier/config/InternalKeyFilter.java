package com.deeptier.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards /internal/** endpoints with the X-Internal-Key header.
 * The Python AI service (and Joule via SAP Build Actions) must supply this key.
 */
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    private final String expectedKey;

    public InternalKeyFilter(DeepTierProperties props) {
        this.expectedKey = props.internal().key();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/internal/")) {
            String provided = request.getHeader("X-Internal-Key");
            if (!expectedKey.equals(provided)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-Internal-Key\"}"
                );
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
