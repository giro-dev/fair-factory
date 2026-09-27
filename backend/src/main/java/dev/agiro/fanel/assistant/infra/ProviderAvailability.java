package dev.agiro.fanel.assistant.infra;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Whether a provider can actually be used on this instance. Spring AI auto-configures
 * OpenAI/Anthropic chat models even without credentials, so bean presence alone is not enough:
 * those providers also require a non-blank API key. Ollama is a local server and needs no key.
 */
@Component
public class ProviderAvailability {
    private final ObjectProvider<OpenAiChatModel> openAi;
    private final ObjectProvider<OllamaChatModel> ollama;
    private final ObjectProvider<AnthropicChatModel> anthropic;
    private final String openAiApiKey;
    private final String anthropicApiKey;

    public ProviderAvailability(ObjectProvider<OpenAiChatModel> openAi,
                                ObjectProvider<OllamaChatModel> ollama,
                                ObjectProvider<AnthropicChatModel> anthropic,
                                @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
                                @Value("${spring.ai.anthropic.api-key:}") String anthropicApiKey) {
        this.openAi = openAi;
        this.ollama = ollama;
        this.anthropic = anthropic;
        this.openAiApiKey = openAiApiKey;
        this.anthropicApiKey = anthropicApiKey;
    }

    public boolean isAvailable(String provider) {
        if (provider == null) {
            return false;
        }
        return switch (provider.toLowerCase()) {
            case "openai" -> openAi.getIfAvailable() != null && !openAiApiKey.isBlank();
            case "ollama" -> ollama.getIfAvailable() != null;
            case "anthropic" -> anthropic.getIfAvailable() != null && !anthropicApiKey.isBlank();
            default -> false;
        };
    }

    /** The provider's {@link ChatModel} bean, only when the provider is usable. */
    public Optional<ChatModel> chatModel(String provider) {
        if (!isAvailable(provider)) {
            return Optional.empty();
        }
        ChatModel model = switch (provider.toLowerCase()) {
            case "openai" -> openAi.getIfAvailable();
            case "ollama" -> ollama.getIfAvailable();
            case "anthropic" -> anthropic.getIfAvailable();
            default -> null;
        };
        return Optional.ofNullable(model);
    }
}
