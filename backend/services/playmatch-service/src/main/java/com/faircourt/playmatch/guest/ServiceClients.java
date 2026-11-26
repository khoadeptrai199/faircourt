package com.faircourt.playmatch.guest;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient}s playmatch-service uses to call other services (AD-15): short timeouts (owner decision
 * 2026-11-17: 2 s connect, 5 s read), so a service that does not answer in time becomes a 503. This service has no
 * auto-configured client builder (no extra dependency was approved), so the factory is set up here.
 */
@Component
public class ServiceClients {

    private final Duration connectTimeout;
    private final Duration readTimeout;

    ServiceClients(
            @Value("${faircourt.services.connect-timeout}") Duration connectTimeout,
            @Value("${faircourt.services.read-timeout}") Duration readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public RestClient create(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
