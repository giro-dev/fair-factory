package dev.agiro.fanel.assistant.api;

import java.util.List;

/**
 * A model provider and the models discovered on it. {@code available} means the corresponding
 * ChatModel bean exists (credentials/endpoint configured); {@code error} carries the discovery
 * failure when the provider is available but listing its models failed.
 */
public record ProviderDto(String provider, boolean available, List<String> models, String error) {
}
