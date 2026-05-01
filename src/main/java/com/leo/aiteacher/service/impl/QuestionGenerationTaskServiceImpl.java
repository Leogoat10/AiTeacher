package com.leo.aiteacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    @Value("${deepseek.api.model:deepseek-v4-flash}")
    private String modelName;

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
                                                    String questionCount, String customMessage, Integer conversationId,
                                                    Boolean useContext, Integer contextRounds) {
        Map<String, Object> result = new HashMap<>();

        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        boolean enableContext = useContext == null || useContext;
        int actualContextRounds = normalizeContextRounds(contextRounds);

        if (enableContext && conversationId == null) {
            result.put("success", false);
            result.put("error", "当前会话暂无历史对话，无法使用参考上下文模式");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        Integer actualConversationId = conversationId;
        boolean isNewConversation = false;
        ConversationDto existingConversation = null;

        if (actualConversationId == null) {
            ConversationDto newConversation = new ConversationDto();
            newConversation.setTeacherId(teacher.getTeacherId());
            newConversation.setTitle("请发送消息");
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
            existingConversation = conversation;
        }

        String resolvedSubject = subject;
        String resolvedGrade = grade;
        String resolvedDifficulty = difficulty;
        String resolvedQuestionType = questionType;
        String resolvedQuestionCount = questionCount;

        if (enableContext) {
            GenerationTaskDto latestTask = findLatestTask(teacher.getTeacherId(), actualConversationId);
            if (latestTask != null) {
                if (isBlank(resolvedSubject)) resolvedSubject = latestTask.getSubject();
                if (isBlank(resolvedGrade)) resolvedGrade = latestTask.getGrade();
                if (isBlank(resolvedDifficulty)) resolvedDifficulty = latestTask.getDifficulty();
                if (isBlank(resolvedQuestionType)) resolvedQuestionType = latestTask.getQuestionType();
                if (isBlank(resolvedQuestionCount) && latestTask.getQuestionCount() != null) {
                    resolvedQuestionCount = String.valueOf(latestTask.getQuestionCount());
                }
            }
        }

        String validationError = validateRequest(resolvedSubject, resolvedGrade, resolvedDifficulty, resolvedQuestionType, resolvedQuestionCount);
        if (validationError != null) {
            result.put("success", false);
            result.put("error", validationError);
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        String recentContextSummary = enableContext
                ? buildRecentContextSummary(actualConversationId, actualContextRounds)
                : "";
        if (enableContext && recentContextSummary.isBlank()) {
            result.put("success", false);
            result.put("error", "当前会话暂无历史对话，无法使用参考上下文模式");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        String title = buildTitleFromForm(resolvedSubject, resolvedGrade, resolvedDifficulty, resolvedQuestionType, resolvedQuestionCount, customMessage);
        if (isNewConversation || shouldRefreshConversationTitle(existingConversation)) {
            ConversationDto conversationToUpdate = new ConversationDto();
            conversationToUpdate.setId(actualConversationId);
            conversationToUpdate.setTitle(title);
            conversationMapper.updateById(conversationToUpdate);
        }
        String prompt = buildStructuredPrompt(
                resolvedSubject, resolvedGrade, resolvedDifficulty, resolvedQuestionType, resolvedQuestionCount, customMessage,
                recentContextSummary, enableContext, actualContextRounds
        );

        GenerationTaskDto task = new GenerationTaskDto();
        task.setTeacherId(teacher.getTeacherId());
        task.setConversationId(actualConversationId);
        task.setStatus("PENDING");
        task.setSubject(resolvedSubject);
        task.setGrade(resolvedGrade);
        task.setDifficulty(resolvedDifficulty);
        task.setQuestionType(resolvedQuestionType);
        task.setQuestionCount(parseQuestionCount(resolvedQuestionCount));
        task.setCustomMessage(customMessage);
        task.setRequestPrompt(prompt);
        task.setQualityPassed(false);
        generationTaskMapper.insert(task);

        final Integer finalConversationId = actualConversationId;
        final String finalTitle = title;
        questionGenerationExecutor.execute(() -> executeTask(task.getId(), finalConversationId, finalTitle));

        result.put("success", true);
        result.put("taskId", task.getId());
        result.put("conversationId", actualConversationId);
        result.put("status", task.getStatus());
        result.put("useContext", enableContext);
        result.put("contextRounds", actualContextRounds);
        if (isNewConversation) {
            result.put("newConversationId", actualConversationId);
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean shouldRefreshConversationTitle(ConversationDto conversation) {
        if (conversation == null) {
            return false;
        }
        String currentTitle = conversation.getTitle();
        return currentTitle == null
                || currentTitle.isBlank()
                || "请发送消息".equals(currentTitle.trim());
    }

    private GenerationTaskDto findLatestTask(Integer teacherId, Integer conversationId) {
        return generationTaskMapper.selectOne(
                new QueryWrapper<GenerationTaskDto>()
                        .eq("teacher_id", teacherId)
                        .eq("conversation_id", conversationId)
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );
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

    private void executeTask(Long taskId, Integer conversationId, String title) {
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
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(
                resolveChatCompletionsUrl(deepSeekApiUrl),
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
            return Integer.parseInt(questionCount == null ? "" : questionCount.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private String buildStructuredPrompt(String subject, String grade, String difficulty, String questionType,
                                         String questionCount, String customMessage, String recentContextSummary,
                                         boolean useContext, int contextRounds) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(buildSystemRulesTemplate(useContext));
        prompt.append("\n\n");
        prompt.append(buildContextTemplate(useContext, contextRounds, recentContextSummary));
        prompt.append("\n\n");
        prompt.append(buildCurrentRequestTemplate(subject, grade, difficulty, questionType, questionCount, customMessage, useContext));
        prompt.append("\n\n");
        prompt.append(buildOutputJsonTemplate(questionType, difficulty, useContext));
        return prompt.toString();
    }

    private String buildSystemRulesTemplate(boolean useContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是专业教学题目生成助手。请严格遵守以下规则：\n");
        prompt.append("1) 必须输出合法JSON，不允许输出JSON以外内容。\n");
        prompt.append("2) 题目数量必须与请求一致。\n");
        prompt.append("3) 每题必须包含：题干、题型、答案、解析。\n");
        prompt.append("4) 同一批次题目尽量避免重复。\n");
        prompt.append("5) 当历史上下文与本轮参数冲突时，优先级为：本轮参数 > 本轮补充要求 > 历史上下文。\n");
        prompt.append("6) 数学公式必须使用LaTeX格式：行内公式用 \\(公式\\)，独立公式用 \\[公式\\]。禁止使用 $公式$ 或 $$公式$$ 格式。\n");
        if (useContext) {
            prompt.append("6) 当前是“上下文改写模式”：默认基于最近一轮题目做定向改写，不要重新生成完全不同的新题组。\n");
            prompt.append("8) 若用户未明确要求改变科目/题型/题量，必须保持这些维度与最近一轮一致。\n");
            prompt.append("8) 用户若仅要求“更难/更简单/更贴近应用”，只能做对应维度调整，其他维度保持不变。\n");
        } else {
            prompt.append("7) 普通模式下按本轮参数独立生成。\n");
        }
        return prompt.toString();
    }

    private String buildContextTemplate(boolean useContext, int contextRounds, String recentContextSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【历史上下文】\n");
        if (!useContext) {
            prompt.append("本轮未启用历史上下文，请按本轮参数独立生成。\n");
            return prompt.toString();
        }
        prompt.append("本轮已启用历史上下文，最多关联最近").append(contextRounds).append("轮。\n");
        if (recentContextSummary == null || recentContextSummary.isBlank()) {
            prompt.append("当前会话暂无可用历史轮次，请按本轮参数独立生成。\n");
        } else {
            prompt.append(recentContextSummary).append("\n");
        }
        return prompt.toString();
    }

    private String buildCurrentRequestTemplate(String subject, String grade, String difficulty, String questionType,
                                               String questionCount, String customMessage, boolean useContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【本轮请求】\n");
        prompt.append("- 科目：").append(subject).append("\n");
        prompt.append("- 年级：").append(grade).append("\n");
        prompt.append("- 难度：").append(difficulty).append("\n");
        prompt.append("- 题型：").append(questionType).append("\n");
        prompt.append("- 数量：").append(questionCount).append("\n");
        if (customMessage != null && !customMessage.isBlank()) {
            prompt.append(useContext ? "- 增量改写指令：" : "- 补充要求：").append(customMessage).append("\n");
        }
        prompt.append("- 执行约束：\n");
        if (useContext) {
            prompt.append("  - 这是基于历史题组的增量改写，不是全新命题。\n");
            prompt.append("  - 若增量指令不明确，优先保持上一轮题组结构与主题，仅微调表述与难度。\n");
            prompt.append("  - 除非明确要求“重出一套/完全不同”，禁止跳到无关主题。\n");
        } else {
            prompt.append("  - 若表达“再难一些/再简单一些”，仅调整难度，不改变题型与题量（除非本轮参数明确修改）。\n");
            prompt.append("  - 若表达“换题型”，保留科目和年级，按新题型重生成。\n");
        }
        return prompt.toString();
    }

    private String buildOutputJsonTemplate(String questionType, String difficulty, boolean useContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【输出格式】\n");
        prompt.append("必须返回严格JSON（不允许markdown代码块），格式如下：\n");
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
        prompt.append("示例：题干可以写成 \"计算 \\\\(x^2 + y^2\\\\) 的值\" 或 \"方程 \\\\[E = mc^2\\\\] 表示质能关系\"\n");
        if (useContext) {
            prompt.append("额外要求：在保证JSON结构不变前提下，输出内容应体现“基于历史题组改写”的连续性。\n");
        }
        return prompt.toString();
    }

    private int normalizeContextRounds(Integer contextRounds) {
        if (contextRounds == null) {
            return 5;
        }
        if (contextRounds < 1) {
            return 1;
        }
        return Math.min(contextRounds, 5);
    }

    private String buildRecentContextSummary(Integer conversationId, int rounds) {
        List<MessageDto> allMessages = messageMapper.getMessagesByConversationId(conversationId);
        if (allMessages == null || allMessages.isEmpty()) {
            return "";
        }

        List<MessageDto> validRounds = allMessages.stream()
                .filter(message -> message != null
                        && message.getQuestion() != null && !message.getQuestion().isBlank()
                        && message.getAnswer() != null && !message.getAnswer().isBlank()
                        && !"FAILED".equalsIgnoreCase(message.getStructuredStatus()))
                .toList();

        if (validRounds.isEmpty()) {
            return "";
        }

        int from = Math.max(validRounds.size() - rounds, 0);
        List<MessageDto> recentRounds = validRounds.subList(from, validRounds.size());

        StringBuilder summary = new StringBuilder();
        MessageDto latest = recentRounds.get(recentRounds.size() - 1);
        summary.append("【最近一轮基准题组】\n");
        summary.append("- 用户请求：").append(abbreviate(latest.getQuestion(), 120)).append("\n");
        summary.append("- 题组摘要：").append(abbreviate(extractAnswerSummary(latest.getAnswer()), 1200)).append("\n\n");
        summary.append("【历史轮次摘要】\n");
        for (int i = 0; i < recentRounds.size(); i++) {
            MessageDto msg = recentRounds.get(i);
            summary.append("Round ").append(i + 1).append(":\n");
            summary.append("- 用户意图摘要：").append(abbreviate(msg.getQuestion(), 90)).append("\n");
            summary.append("- AI产出摘要：").append(abbreviate(extractAnswerSummary(msg.getAnswer()), 320)).append("\n");
        }
        return summary.toString().trim();
    }

    private String extractAnswerSummary(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String normalized = answer.replace("\r\n", "\n").trim();
        List<String> stems = extractQuestionStems(normalized);
        if (!stems.isEmpty()) {
            StringBuilder summary = new StringBuilder();
            summary.append("共").append(stems.size()).append("题；题干摘要：");
            for (int i = 0; i < stems.size(); i++) {
                if (i > 0) {
                    summary.append(" | ");
                }
                summary.append(i + 1).append(")").append(stems.get(i));
            }
            return summary.toString();
        }

        String[] lines = normalized.split("\n");
        StringBuilder fallback = new StringBuilder();
        int maxLines = Math.min(lines.length, 6);
        for (int i = 0; i < maxLines; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (fallback.length() > 0) {
                fallback.append(" | ");
            }
            fallback.append(line);
        }
        return fallback.length() > 0 ? fallback.toString() : normalized;
    }

    private List<String> extractQuestionStems(String markdownAnswer) {
        String[] lines = markdownAnswer.split("\n");
        List<String> stems = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.matches("^\\d+\\.\\s+.*")) {
                String stem = line.replaceFirst("^\\d+\\.\\s+", "").trim();
                if (!stem.isEmpty()) {
                    stems.add(stem);
                }
            }
        }
        return stems;
    }

    private String abbreviate(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String cleaned = content.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, maxLength) + "...";
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
