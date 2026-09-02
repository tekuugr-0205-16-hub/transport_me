package com.mobilityos.route.navigation.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mobilityos.navigation.google")
public class GoogleRoutesProperties {

    private String apiKey;

    private String baseUrl =
            "https://routes.googleapis.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(
            String apiKey
    ) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(
            String baseUrl
    ) {
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return apiKey != null
                && !apiKey.isBlank();
    }
}