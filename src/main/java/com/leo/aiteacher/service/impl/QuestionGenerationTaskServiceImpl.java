package com.leo.aiteacher.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.pojo.dto.ConversationDto;
import com.leo.aiteacher.pojo.dto.GenerationTaskDto;
import com.leo.aiteacher.pojo.dto.MessageDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.ConversationMapper;
import com.leo.aiteacher.pojo.mapper.GenerationTaskMapper;
import com.leo.aiteacher.pojo.mapper.MessageMapper;
import com.leo.aiteacher.service.QuestionGenerationTaskService;
import com.leo.aiteacher.util.SessionUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class QuestionGenerationTaskServiceImpl implements QuestionGenerationTaskService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionGenerationTaskServiceImpl.class);

    @Value("${deepseek.api.url}")
    private String deepSeekApiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Resource
    private GenerationTaskMapper generationTaskMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    @Qualifier("questionGenerationExecutor")
    private Executor questionGenerationExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> createGenerationTask(String subject, String grade, String difficulty, String questionType,
                                                    String questionCount, String customMessage, Integer conversationId) {
        Map<String, Object> result = new HashMap<>();

        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        String validationError = validateRequest(subject, grade, difficulty, questionType, questionCount);
        if (validationError != null) {
            result.put("success", false);
            result.put("error", validationError);
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        String title = buildTitleFromForm(subject, grade, difficulty, questionType, questionCount, customMessage);
        String prompt = buildStructuredPrompt(subject, grade, difficulty, questionType, questionCount, customMessage);

        Integer actualConversationId = conversationId;
        boolean isNewConversation = false;

        if (actualConversationId == null) {
            ConversationDto newConversation = new ConversationDto();
            newConversation.setTeacherId(teacher.getTeacherId());
            newConversation.setTitle(title);
            conversationMapper.insertConversation(newConversation);
            actualConversationId = newConversation.getId();
            isNewConversation = true;
        } else {
            ConversationDto conversation = conversationMapper.getConversationById(actualConversationId);
            if (conversation == null || !conversation.getTeacherId().equals(teacher.getTeacherId())) {
                result.put("success", false);
                result.put("error", "无权限访问该对话");
                result.put("status", HttpStatus.FORBIDDEN.value());
                return result;
            }
        }

        GenerationTaskDto task = new GenerationTaskDto();
        task.setTeacherId(teacher.getTeacherId());
        task.setConversationId(actualConversationId);
        task.setStatus("PENDING");
        task.setSubject(subject);
        task.setGrade(grade);
        task.setDifficulty(difficulty);
        task.setQuestionType(questionType);
        task.setQuestionCount(parseQuestionCount(questionCount));
        task.setCustomMessage(customMessage);
        task.setRequestPrompt(prompt);
        task.setQualityPassed(false);
        generationTaskMapper.insert(task);

        final Integer finalConversationId = actualConversationId;
        final Integer teacherId = teacher.getTeacherId();
        final String finalTitle = title;
        questionGenerationExecutor.execute(() -> executeTask(task.getId(), teacherId, finalConversationId, finalTitle));

        result.put("success", true);
        result.put("taskId", task.getId());
        result.put("conversationId", actualConversationId);
        result.put("status", task.getStatus());
        if (isNewConversation) {
            result.put("newConversationId", actualConversationId);
        }
        return result;
    }

    @Override
    public Map<String, Object> getGenerationTaskStatus(Long taskId) {
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        GenerationTaskDto task = generationTaskMapper.selectById(taskId);
        if (task == null) {
            result.put("success", false);
            result.put("error", "任务不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!task.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限访问该任务");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        result.put("success", true);
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        result.put("conversationId", task.getConversationId());
        result.put("qualityPassed", Boolean.TRUE.equals(task.getQualityPassed()));
        result.put("errorMessage", task.getErrorMessage());
        result.put("createdAt", task.getCreatedAt());
        result.put("updatedAt", task.getUpdatedAt());
        result.put("completedAt", task.getCompletedAt());

        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(task.getResultJson(), new TypeReference<>() {
                });
                result.put("result", parsed);
            } catch (Exception e) {
                result.put("result", task.getResultJson());
            }
        }
        return result;
    }

    private void executeTask(Long taskId, Integer teacherId, Integer conversationId, String title) {
        GenerationTaskDto task = generationTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        try {
            task.setStatus("RUNNING");
            task.setUpdatedAt(LocalDateTime.now());
            generationTaskMapper.updateById(task);

            String rawContent = callDeepSeek(task.getRequestPrompt());
            task.setRawResponse(rawContent);

            JsonNode structured = parseStructuredContent(rawContent);
            List<Map<String, Object>> issues = qualityCheck(structured);

            boolean hasError = issues.stream().anyMatch(it -> "ERROR".equals(String.valueOf(it.get("severity"))));
            boolean hasWarning = issues.stream().anyMatch(it -> "WARNING".equals(String.valueOf(it.get("severity"))));

            Map<String, Object> resultPayload = new HashMap<>();
            resultPayload.put("questions", structured.path("questions"));
            resultPayload.put("issues", issues);
            task.setResultJson(objectMapper.writeValueAsString(resultPayload));

            if (hasError) {
                task.setStatus("FAILED");
                task.setQualityPassed(false);
                task.setErrorMessage("质量校验未通过，请调整参数后重试");
            } else {
                task.setStatus(hasWarning ? "COMPLETED_WITH_WARNINGS" : "SUCCESS");
                task.setQualityPassed(!hasWarning);
                task.setErrorMessage(null);

                MessageDto message = new MessageDto();
                message.setConversationId(conversationId);
                message.setQuestion(title);
                message.setAnswer(buildMarkdownFromQuestions(structured.path("questions")));
                message.setUserPrompt(task.getRequestPrompt());
                message.setRawModelResponse(rawContent);
                message.setStructuredStatus(task.getStatus());
                messageMapper.insert(message);
            }

            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            generationTaskMapper.updateById(task);

        } catch (Exception e) {
            logger.error("生成任务执行失败, taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setQualityPassed(false);
            task.setErrorMessage("生成失败: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            generationTaskMapper.updateById(task);
        }
    }

    private String callDeepSeek(String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("API密钥未配置");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是专业教学题目生成助手。必须严格输出JSON，不要输出JSON以外内容。"
        ));
        messages.add(Map.of(
                "role", "user",
                "content", prompt
        ));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                deepSeekApiUrl,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class
        );

        JsonNode rootNode = objectMapper.readTree(response.getBody());
        JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
        return contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");
    }

    private JsonNode parseStructuredContent(String rawContent) throws Exception {
        String trimmed = rawContent == null ? "" : rawContent.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json\\s*", "");
            trimmed = trimmed.replaceFirst("^```\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (Exception ex) {
            int first = trimmed.indexOf('{');
            int last = trimmed.lastIndexOf('}');
            if (first >= 0 && last > first) {
                String possibleJson = trimmed.substring(first, last + 1);
                node = objectMapper.readTree(possibleJson);
            } else {
                throw ex;
            }
        }
        if (!node.has("questions") || !node.path("questions").isArray()) {
            throw new RuntimeException("模型返回格式不符合要求，缺少 questions 数组");
        }
        return node;
    }

    private List<Map<String, Object>> qualityCheck(JsonNode node) {
        List<Map<String, Object>> issues = new ArrayList<>();
        JsonNode questions = node.path("questions");
        for (int i = 0; i < questions.size(); i++) {
            JsonNode q = questions.get(i);
            String stem = q.path("stem").asText("");
            String type = q.path("type").asText("");
            String answer = q.path("answer").asText("");

            if (stem.isBlank()) {
                issues.add(buildIssue("ERROR", i + 1, "题干不能为空"));
            }
            if (type.isBlank()) {
                issues.add(buildIssue("ERROR", i + 1, "题型不能为空"));
            }
            if (answer.isBlank()) {
                issues.add(buildIssue("ERROR", i + 1, "答案不能为空"));
            }

            if (type.contains("选择")) {
                JsonNode options = q.path("options");
                if (!options.isArray() || options.size() < 2) {
                    issues.add(buildIssue("ERROR", i + 1, "选择题至少需要2个选项"));
                } else if (!answerMatchesOptions(answer, options)) {
                    issues.add(buildIssue("WARNING", i + 1, "答案与选项格式可能不一致，请人工复核"));
                }
            }
        }
        return issues;
    }

    private Map<String, Object> buildIssue(String severity, int questionIndex, String message) {
        Map<String, Object> issue = new HashMap<>();
        issue.put("severity", severity);
        issue.put("questionIndex", questionIndex);
        issue.put("message", message);
        return issue;
    }

    private boolean answerMatchesOptions(String answer, JsonNode options) {
        String normalized = answer.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 1 && normalized.charAt(0) >= 'A' && normalized.charAt(0) <= 'Z') {
            return true;
        }

        for (JsonNode option : options) {
            String optionText = option.asText("").trim();
            if (optionText.isEmpty()) {
                continue;
            }
            if (optionText.equalsIgnoreCase(answer.trim())) {
                return true;
            }
            if (optionText.length() >= 2 && optionText.charAt(1) == '.') {
                String key = optionText.substring(0, 1);
                if (key.equalsIgnoreCase(answer.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildMarkdownFromQuestions(JsonNode questions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            JsonNode q = questions.get(i);
            sb.append(i + 1).append(". ").append(q.path("stem").asText("")).append("\n");

            JsonNode options = q.path("options");
            if (options.isArray() && !options.isEmpty()) {
                for (int j = 0; j < options.size(); j++) {
                    sb.append("   ").append(options.get(j).asText("")).append("\n");
                }
            }

            sb.append("   答案：").append(q.path("answer").asText("")).append("\n");
            if (!q.path("analysis").asText("").isBlank()) {
                sb.append("   解析：").append(q.path("analysis").asText("")).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String validateRequest(String subject, String grade, String difficulty, String questionType, String questionCount) {
        if (subject == null || subject.isBlank()) {
            return "科目不能为空";
        }
        if (grade == null || grade.isBlank()) {
            return "年级不能为空";
        }
        if (difficulty == null || difficulty.isBlank()) {
            return "难度不能为空";
        }
        if (questionType == null || questionType.isBlank()) {
            return "题型不能为空";
        }
        int count = parseQuestionCount(questionCount);
        if (count <= 0 || count > 100) {
            return "题目数量必须在1-100之间";
        }
        return null;
    }

    private int parseQuestionCount(String questionCount) {
        try {
            return Integer.parseInt(questionCount);
        } catch (Exception e) {
            return -1;
        }
    }

    private String buildStructuredPrompt(String subject, String grade, String difficulty, String questionType,
                                         String questionCount, String customMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为教师生成题目，要求如下：\n");
        prompt.append("- 科目：").append(subject).append("\n");
        prompt.append("- 年级：").append(grade).append("\n");
        prompt.append("- 难度：").append(difficulty).append("\n");
        prompt.append("- 题型：").append(questionType).append("\n");
        prompt.append("- 数量：").append(questionCount).append("\n");
        if (customMessage != null && !customMessage.isBlank()) {
            prompt.append("- 额外要求：").append(customMessage).append("\n");
        }

        prompt.append("\n必须返回严格JSON（不允许markdown代码块），格式如下：\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"stem\": \"题干\",\n");
        prompt.append("      \"type\": \"").append(questionType).append("\",\n");
        prompt.append("      \"options\": [\"A. ...\", \"B. ...\"],\n");
        prompt.append("      \"answer\": \"A\",\n");
        prompt.append("      \"analysis\": \"解析\",\n");
        prompt.append("      \"difficulty\": \"").append(difficulty).append("\",\n");
        prompt.append("      \"knowledgePoints\": [\"知识点1\"],\n");
        prompt.append("      \"score\": 5\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        return prompt.toString();
    }

    private String buildTitleFromForm(String subject, String grade, String difficulty, String questionType,
                                      String questionCount, String customMessage) {
        StringBuilder title = new StringBuilder();
        title.append(subject).append(" ").append(grade).append(" ").append(difficulty).append(" ").append(questionType);
        if (questionCount != null && !questionCount.trim().isEmpty()) {
            title.append(" ").append(questionCount).append("题");
        }
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            String customPart = customMessage.length() > 20
                    ? customMessage.substring(0, 20) + "..."
                    : customMessage;
            title.append(" - ").append(customPart);
        }
        return title.toString();
    }
}
