package dev.agiro.fanel.assistant.infra;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderAvailabilityTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    private ProviderAvailability availability(OpenAiChatModel openAi, OllamaChatModel ollama,
                                              AnthropicChatModel anthropic,
                                              String openAiKey, String anthropicKey) {
        return new ProviderAvailability(provider(openAi), provider(ollama), provider(anthropic),
                openAiKey, anthropicKey);
    }

    @Test
    void openAiRequiresBeanAndNonBlankKey() {
        OpenAiChatModel bean = mock(OpenAiChatModel.class);

        assertThat(availability(bean, null, null, "", "").isAvailable("openai")).isFalse();
        assertThat(availability(bean, null, null, "   ", "").isAvailable("openai")).isFalse();
        assertThat(availability(bean, null, null, "sk-test", "").isAvailable("openai")).isTrue();
        assertThat(availability(bean, null, null, "sk-test", "").isAvailable("OPENAI")).isTrue();
        assertThat(availability(null, null, null, "sk-test", "").isAvailable("openai")).isFalse();
    }

    @Test
    void anthropicRequiresBeanAndNonBlankKey() {
        AnthropicChatModel bean = mock(AnthropicChatModel.class);

        assertThat(availability(null, null, bean, "", "").isAvailable("anthropic")).isFalse();
        assertThat(availability(null, null, bean, "", "sk-ant").isAvailable("anthropic")).isTrue();
    }

    @Test
    void ollamaOnlyRequiresTheBean() {
        assertThat(availability(null, mock(OllamaChatModel.class), null, "", "")
                .isAvailable("ollama")).isTrue();
        assertThat(availability(null, null, null, "", "").isAvailable("ollama")).isFalse();
    }

    @Test
    void unknownOrNullProviderIsUnavailable() {
        ProviderAvailability availability = availability(mock(OpenAiChatModel.class),
                mock(OllamaChatModel.class), mock(AnthropicChatModel.class), "k", "k");
        assertThat(availability.isAvailable("mistral")).isFalse();
        assertThat(availability.isAvailable(null)).isFalse();
    }

    @Test
    void chatModelReturnsBeanOnlyWhenAvailable() {
        OpenAiChatModel bean = mock(OpenAiChatModel.class);
        assertThat(availability(bean, null, null, "", "").chatModel("openai")).isEmpty();
        assertThat(availability(bean, null, null, "sk", "").chatModel("openai")).contains(bean);
    }
}
