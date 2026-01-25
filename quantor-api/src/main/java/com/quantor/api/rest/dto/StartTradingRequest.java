package com.quantor.api.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Authenticated start request.
 *
 * userId is derived from the access token subject (JWT sub).
 */
public record StartTradingRequest(
        @NotBlank String accountId
) {}
