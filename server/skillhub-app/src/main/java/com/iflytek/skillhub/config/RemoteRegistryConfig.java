package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.registry.remote.RemoteRegistryClient;
import com.iflytek.skillhub.infra.registry.clawhub.ClawHubRemoteRegistryClient;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class RemoteRegistryConfig {

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.remote-registry.clawhub", name = "enabled", havingValue = "true")
    public RemoteRegistryClient remoteRegistryClient(WebClient.Builder webClientBuilder,
                                                     RemoteRegistryProperties properties,
                                                     SkillPublishProperties skillPublishProperties) {
        int maxInMemorySize = Math.toIntExact(skillPublishProperties.getMaxPackageSize());
        WebClient webClient = webClientBuilder.clone()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .build();
        return new ClawHubRemoteRegistryClient(
                webClient,
                URI.create(trimTrailingSlash(properties.getBaseUrl())),
                properties.getApiBasePath(),
                properties.buildAuthorizationHeader(),
                skillPublishProperties.getMaxPackageSize()
        );
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://clawhub.ai";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
