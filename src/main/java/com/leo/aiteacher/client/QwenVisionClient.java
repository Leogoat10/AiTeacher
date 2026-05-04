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
public class QwenVisionClient {

    private static final Logger logger = LoggerFactory.getLogger(QwenVisionClient.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.api.key:}")
    private String apiKey;

    @Value("${qwen.api.model:qwen3.6-flash}")
    private String modelName;

    @Value("${qwen.api.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${qwen.api.retry.delay-ms:500}")
    private long retryDelayMs;

    public QwenVisionClient(RestTemplateBuilder restTemplateBuilder,
                            ObjectMapper objectMapper,
                            @Value("${qwen.api.timeout.connect-ms:5000}") long connectTimeoutMs,
                            @Value("${qwen.api.timeout.read-ms:60000}") long readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    public OcrResult recognizeTextFromImageDataUrl(String imageDataUrl) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("未配置通义OCR API Key");
        }
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            throw new RuntimeException("图片数据为空");
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            long start = System.currentTimeMillis();
            try {
                String rawResponse = executeOcr(imageDataUrl);
                String content = extractContent(rawResponse).trim();
                long latencyMs = System.currentTimeMillis() - start;
                logger.info("通义OCR成功，attempt={}, latencyMs={}, model={}", attempt, latencyMs, modelName);
                return new OcrResult(content, rawResponse, modelName, latencyMs, attempt);
            } catch (ResourceAccessException e) {
                lastException = e;
                logger.warn("通义OCR超时/网络错误，attempt={}/{}", attempt, maxAttempts);
            } catch (Exception e) {
                lastException = e;
                logger.warn("通义OCR调用失败，attempt={}/{}, error={}", attempt, maxAttempts, e.getMessage());
            }

            if (attempt < Math.max(1, maxAttempts)) {
                TimeUnit.MILLISECONDS.sleep(Math.max(0L, retryDelayMs));
            }
        }

        throw lastException == null ? new RuntimeException("通义OCR调用失败") : lastException;
    }

    private String executeOcr(String imageDataUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "请执行OCR识别，完整输出图片中的文字。只返回识别文本，不要解释，不要Markdown。"),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
                )
        )));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("通义OCR请求，url={}, model={}, imageChars={}", resolveChatCompletionsUrl(apiUrl), modelName, imageDataUrl.length());
        ResponseEntity<String> response = restTemplate.exchange(resolveChatCompletionsUrl(apiUrl), HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("通义OCR响应异常: " + response.getStatusCode());
        }
        return response.getBody() == null ? "" : response.getBody();
    }

    private String extractContent(String rawResponse) throws Exception {
        JsonNode rootNode = objectMapper.readTree(rawResponse);
        JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText("");
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                if (item.has("text")) {
                    String text = item.path("text").asText("");
                    if (!text.isBlank()) {
                        if (builder.length() > 0) builder.append("\n");
                        builder.append(text);
                    }
                }
            }
            return builder.toString();
        }
        if (contentNode.isObject() && contentNode.has("text")) {
            return contentNode.path("text").asText("");
        }
        return "";
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

    public record OcrResult(String text, String rawResponse, String modelName, long latencyMs, int attempt) {
    }
}
