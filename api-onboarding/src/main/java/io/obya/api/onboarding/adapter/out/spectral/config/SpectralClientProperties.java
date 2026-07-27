package io.obya.api.onboarding.adapter.out.spectral.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;

@ConfigurationProperties(prefix = "spectral")
public record SpectralClientProperties(@NotBlank URL baseUrl) {}
