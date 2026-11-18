package com.faircourt.auth.account;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /internal/v1/auth/verify}: internal route, called only by the gateway over the private Compose network
 * (AD-15; the gateway's public route table never lists {@code /internal/**}). Read-only; no side effects.
 */
@RestController
public class AccountVerifyController {

    private final AccountVerifyService service;

    public AccountVerifyController(AccountVerifyService service) {
        this.service = service;
    }

    @PostMapping(value = "/internal/v1/auth/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccountIdentityResponse verify(@Valid @RequestBody AccountVerifyRequest request) {
        return service.verify(request.email(), request.password());
    }
}
