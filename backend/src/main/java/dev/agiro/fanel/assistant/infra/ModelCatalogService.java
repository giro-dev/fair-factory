package dev.agiro.fanel.assistant.infra;

import dev.agiro.fanel.assistant.api.ProviderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers the models exposed by each usable provider (Ollama, OpenAI, Anthropic).
 * Results are cached for 5 minutes; a provider that is not usable on this instance
 * (see {@link ProviderAvailability}) is reported unavailable and is never called.
 */
@Component
public class ModelCatalogService {
    private static final Logger log = LoggerFactory.getLogger(ModelCatalogService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final List<String> PROVIDERS = List.of("openai", "ollama", "anthropic");

    private final ProviderAvailability providers;
    private final ObjectProvider<OllamaApi> ollamaApi;
    private final RestClient restClient;
    private final String openAiApiKey;
    private final String openAiBaseUrl;
    private final String anthropicApiKey;
    private final String anthropicBaseUrl;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(ProviderDto dto, Instant fetchedAt) {}

    private record ModelList(List<ModelId> data) {
        record ModelId(String id) {}
    }

    public ModelCatalogService(ProviderAvailability providers,
                               ObjectProvider<OllamaApi> ollamaApi,
                               RestClient.Builder restClientBuilder,
                               @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
                               @Value("${spring.ai.openai.base-url:https://api.openai.com}") String openAiBaseUrl,
                               @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey,
                               @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}") String anthropicBaseUrl) {
        this.providers = providers;
        this.ollamaApi = ollamaApi;
        this.openAiApiKey = openAiApiKey;
        this.openAiBaseUrl = openAiBaseUrl;
        this.anthropicApiKey = anthropicApiKey;
        this.anthropicBaseUrl = anthropicBaseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public List<ProviderDto> providers(boolean refresh) {
        return PROVIDERS.stream().map(p -> provider(p, refresh)).toList();
    }

    private ProviderDto provider(String provider, boolean refresh) {
        if (!refresh) {
            CacheEntry cached = cache.get(provider);
            if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
                return cached.dto();
            }
        }
        ProviderDto dto = discover(provider);
        cache.put(provider, new CacheEntry(dto, Instant.now()));
        return dto;
    }

    private ProviderDto discover(String provider) {
        if (!providers.isAvailable(provider)) {
            return new ProviderDto(provider, false, List.of(), null);
        }
        try {
            List<String> models = switch (provider) {
                case "openai" -> listOpenAiModels();
                case "ollama" -> listOllamaModels();
                case "anthropic" -> listAnthropicModels();
                default -> List.<String>of();
            };
            return new ProviderDto(provider, true, models, null);
        } catch (Exception e) {
            log.warn("Failed to list {} models: {}", provider, e.toString());
            return new ProviderDto(provider, true, List.of(), e.getMessage());
        }
    }

    private List<String> listOllamaModels() {
        OllamaApi api = ollamaApi.getIfAvailable();
        if (api == null) {
            return List.of();
        }
        OllamaApi.ListModelResponse response = api.listModels();
        return response == null || response.models() == null
                ? List.of()
                : response.models().stream().map(OllamaApi.Model::name).sorted().toList();
    }

    private List<String> listOpenAiModels() {
        ModelList response = restClient.get()
                .uri(openAiBaseUrl + "/v1/models")
                .header("Authorization", "Bearer " + openAiApiKey)
                .retrieve()
                .body(ModelList.class);
        return ids(response);
    }

    private List<String> listAnthropicModels() {
        ModelList response = restClient.get()
                .uri(anthropicBaseUrl + "/v1/models")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .retrieve()
                .body(ModelList.class);
        return ids(response);
    }

    private List<String> ids(ModelList response) {
        return response == null || response.data() == null
                ? List.of()
                : response.data().stream().map(ModelList.ModelId::id).sorted().toList();
    }
}
