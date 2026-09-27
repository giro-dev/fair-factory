package dev.agiro.fanel.assistant.infra;

import dev.agiro.fanel.assistant.domain.ModelProfile;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.ai.tool.ToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChatClientFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ChatClientFactory.class);

    private final ChatClientBuilderConfigurer configurer;
    private final ProviderAvailability providers;
    private final ChatMemory chatMemory;
    private final ObservationRegistry observationRegistry;

    public ChatClientFactory(ChatClientBuilderConfigurer configurer,
                               ProviderAvailability providers,
                               ChatMemory chatMemory,
                               ObservationRegistry observationRegistry) {
        this.configurer = configurer;
        this.providers = providers;
        this.chatMemory = chatMemory;
        this.observationRegistry = observationRegistry;
    }

    public Optional<ChatClient> build(ModelProfile profile, String systemPrompt,
                                      List<Object> toolObjects, List<ToolCallback> toolCallbacks) {
        Optional<ChatModel> resolved = resolve(profile);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        ChatModel chatModel = resolved.get();

        ChatClient.Builder builder = ChatClient.builder(chatModel, observationRegistry, null, null, null);
        builder = configurer.configure(builder);

        ChatOptions.Builder options = ChatOptions.builder();
        if (profile.model() != null) {
            options.model(profile.model());
        }
        if (profile.temperature() != null) {
            options.temperature(profile.temperature());
        }
        if (profile.maxTokens() != null) {
            options.maxTokens(profile.maxTokens());
        }
        builder.defaultOptions(options);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.defaultSystem(systemPrompt);
        }

        builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());

        if (toolObjects != null && !toolObjects.isEmpty()) {
            builder.defaultTools(toolObjects.toArray());
        }
        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            builder.defaultToolCallbacks(toolCallbacks.toArray(new ToolCallback[0]));
        }

        return Optional.of(builder.build());
    }

    /** The provider's {@link ChatModel} bean, when the provider is usable on this instance. */
    public Optional<ChatModel> resolve(ModelProfile profile) {
        if (profile == null || profile.provider() == null) {
            LOG.warn("Cannot resolve ChatModel: profile or provider is null");
            return Optional.empty();
        }
        Optional<ChatModel> model = providers.chatModel(profile.provider());
        if (model.isEmpty()) {
            LOG.warn("No usable ChatModel for provider '{}'", profile.provider());
        } else {
            LOG.info("Resolved ChatModel for provider '{}'", profile.provider());
        }
        return model;
    }
}
