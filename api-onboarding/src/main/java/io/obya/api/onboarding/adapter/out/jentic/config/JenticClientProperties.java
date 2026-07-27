package io.obya.api.onboarding.adapter.out.jentic.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;

@ConfigurationProperties(prefix = "jentic")
public record JenticClientProperties(@NotBlank URL baseUrl) {}
