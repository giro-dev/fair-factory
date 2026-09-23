package dev.agiro.fanel.calendar.infra;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class HttpIcsFetcher implements IcsFetcher {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;

    public HttpIcsFetcher() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String fetch(String url) {
        String body = client.get().uri(toHttpUrl(url)).retrieve().body(String.class);
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("ICS document exceeds 5 MB");
        }
        return body;
    }

    /** Normalizes and validates a subscription URL: webcal:// is rewritten to https://. */
    public static String toHttpUrl(String url) {
        if (url == null) throw new IllegalArgumentException("URL is required");
        String normalized = url.startsWith("webcal://") ? "https://" + url.substring("webcal://".length()) : url;
        String scheme;
        try {
            scheme = URI.create(normalized).getScheme();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid calendar URL: " + url);
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http(s) calendar URLs are supported");
        }
        return normalized;
    }
}
