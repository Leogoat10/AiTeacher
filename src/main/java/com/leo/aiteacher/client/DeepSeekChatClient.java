package com.leo.aiteacher.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekChatClient {

    private static final Logger logger = LoggerFactory.getLogger(DeepSeekChatClient.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.model:deepseek-v4-flash}")
    private String modelName;

    @Value("${deepseek.api.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${deepseek.api.retry.delay-ms:500}")
    private long retryDelayMs;

    @Value("${deepseek.api.log.max-prompt-chars:12000}")
    private int maxPromptLogChars;

    public DeepSeekChatClient(RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper,
                              @Value("${deepseek.api.timeout.connect-ms:5000}") long connectTimeoutMs,
                              @Value("${deepseek.api.timeout.read-ms:300000}") long readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public ChatResult chat(String message) throws Exception {
        return chat("default", message);
    }

    public ChatResult chat(String scene, String message) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            long start = System.currentTimeMillis();
            try {
                String rawResponse = executeChat(scene, message, attempt);
                JsonNode rootNode = objectMapper.readTree(rawResponse);
                JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
                String content = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");
                long latencyMs = System.currentTimeMillis() - start;
                logger.info("DeepSeek响应成功，scene={}, attempt={}, latencyMs={}, model={}",
                        safeScene(scene), attempt, latencyMs, modelName);
                return new ChatResult(content, rawResponse, modelName, latencyMs, attempt);
            } catch (ResourceAccessException e) {
                lastException = e;
                logger.warn("DeepSeek调用超时/网络错误，scene={}, attempt={}/{}",
                        safeScene(scene), attempt, maxAttempts);
            } catch (Exception e) {
                lastException = e;
                logger.warn("DeepSeek调用失败，scene={}, attempt={}/{}, error={}",
                        safeScene(scene), attempt, maxAttempts, e.getMessage());
            }

            if (attempt < Math.max(1, maxAttempts)) {
                try {
                    TimeUnit.MILLISECONDS.sleep(Math.max(0L, retryDelayMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }
        }

        throw lastException == null ? new RuntimeException("DeepSeek调用失败") : lastException;
    }

    private String executeChat(String scene, String message, int attempt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(Map.of(
                "role", "user",
                "content", message
        )));

        String requestJson = objectMapper.writeValueAsString(requestBody);
        logger.info("DeepSeek请求，scene={}, attempt={}, url={}, payload={}",
                safeScene(scene), attempt, resolveChatCompletionsUrl(apiUrl), abbreviate(requestJson));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(resolveChatCompletionsUrl(apiUrl), HttpMethod.POST, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("DeepSeek响应异常: " + response.getStatusCode());
        }

        return response.getBody() == null ? "" : response.getBody();
    }

    public record ChatResult(String content, String rawResponse, String modelName, long latencyMs, int attempt) {}

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(200, maxPromptLogChars);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated, totalChars=" + text.length() + ")";
    }

    private String safeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return "default";
        }
        return scene;
    }

    private String resolveChatCompletionsUrl(String configuredUrl) {
        String normalized = configuredUrl == null ? "" : configuredUrl.trim();
        if (normalized.endsWith("/chat/completions") || normalized.endsWith("/v1/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }
}
