package io.obya.api.onboarding.adapter.out.strapi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;

@ConfigurationProperties(prefix = "strapi")
public record StrapiClientProperties(@NotBlank URL baseUrl) {}
