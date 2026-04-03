package com.leo.aiteacher.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.client.DeepSeekChatClient;
import com.leo.aiteacher.pojo.dto.AssignmentDto;
import com.leo.aiteacher.pojo.dto.GradingTaskDto;
import com.leo.aiteacher.pojo.dto.StudentAnswerDto;
import com.leo.aiteacher.pojo.mapper.AssignmentMapper;
import com.leo.aiteacher.pojo.mapper.GradingTaskMapper;
import com.leo.aiteacher.pojo.mapper.StudentAnswerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Service
public class StudentGradingAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(StudentGradingAsyncService.class);
    private static final String PROMPT_VERSION = "v3.0-week3-structured-json";
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```json\\s*(\\{.*?\\})\\s*```");
    private static final Pattern RAW_JSON_PATTERN = Pattern.compile("(?s)(\\{.*\\})");

    @Value("${grading.task.max-attempts:3}")
    private int maxAttempts;

    @Value("${grading.task.retry-delay-ms:1500}")
    private long retryDelayMs;

    @Autowired
    private GradingTaskMapper gradingTaskMapper;

    @Autowired
    private StudentAnswerMapper studentAnswerMapper;

    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private DeepSeekChatClient deepSeekChatClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Async("gradingExecutor")
    public void processGradingTask(Long gradingTaskId) {
        GradingTaskDto task = gradingTaskMapper.selectById(gradingTaskId);
        if (task == null) {
            logger.warn("判题任务不存在，taskId={}", gradingTaskId);
            return;
        }

        StudentAnswerDto answer = studentAnswerMapper.selectById(task.getAnswerId());
        if (answer == null) {
            markTaskFailed(task, "答案记录不存在");
            return;
        }

        AssignmentDto assignment = assignmentMapper.selectById(answer.getAssignmentId());
        if (assignment == null) {
            markTaskFailed(task, "题目记录不存在");
            markAnswerFailed(answer, "题目记录不存在");
            return;
        }

        task.setStatus("RUNNING");
        task.setLastError(null);
        task.setNextRetryAt(null);
        gradingTaskMapper.updateById(task);

        answer.setGradingStatus("RUNNING");
        answer.setGradingError(null);
        answer.setGradingStartedAt(LocalDateTime.now());
        studentAnswerMapper.updateById(answer);

        Exception lastException = null;
        int attempts = Math.max(1, maxAttempts);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String promptMessage = constructPromptForEvaluation(assignment, answer.getStudentAnswer());
                DeepSeekChatClient.ChatResult chatResult = deepSeekChatClient.chat(promptMessage);
                EvaluationResult evaluation = parseEvaluation(chatResult.content());

                answer.setAiScore(evaluation.score());
                answer.setAiAnalysis(evaluation.analysis());
                answer.setGradingStatus("SUCCESS");
                answer.setGradingError(null);
                answer.setModelName(chatResult.modelName());
                answer.setPromptVersion(PROMPT_VERSION);
                answer.setRawResponse(chatResult.rawResponse());
                answer.setEvaluationJson(evaluation.evaluationJson());
                answer.setGradingCompletedAt(LocalDateTime.now());
                studentAnswerMapper.updateById(answer);

                task.setStatus("SUCCESS");
                task.setRetryCount(attempt - 1);
                task.setLastError(null);
                task.setCompletedAt(LocalDateTime.now());
                gradingTaskMapper.updateById(task);

                logger.info("异步判题完成，taskId={}, answerId={}, attempt={}", task.getId(), answer.getId(), attempt);
                return;
            } catch (Exception e) {
                lastException = e;
                task.setRetryCount(attempt);
                task.setLastError(e.getMessage());
                if (attempt < attempts) {
                    task.setStatus("PENDING");
                    task.setNextRetryAt(LocalDateTime.now().plusNanos(TimeUnit.MILLISECONDS.toNanos(retryDelayMs)));
                    gradingTaskMapper.updateById(task);
                    sleepRetry(retryDelayMs);
                    task.setStatus("RUNNING");
                    task.setNextRetryAt(null);
                    gradingTaskMapper.updateById(task);
                }
                logger.warn("异步判题失败，taskId={}, answerId={}, attempt={}/{}", task.getId(), answer.getId(), attempt, attempts);
            }
        }

        String finalError = lastException == null ? "判题失败" : lastException.getMessage();
        markTaskFailed(task, finalError);
        markAnswerFailed(answer, finalError);
    }

    private void markTaskFailed(GradingTaskDto task, String error) {
        task.setStatus("FAILED");
        task.setLastError(error);
        task.setCompletedAt(LocalDateTime.now());
        gradingTaskMapper.updateById(task);
    }

    private void markAnswerFailed(StudentAnswerDto answer, String error) {
        answer.setGradingStatus("FAILED");
        answer.setGradingError(error);
        answer.setGradingCompletedAt(LocalDateTime.now());
        studentAnswerMapper.updateById(answer);
    }

    private void sleepRetry(long delayMs) {
        try {
            Thread.sleep(Math.max(0L, delayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String constructPromptForEvaluation(AssignmentDto assignment, String studentAnswer) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位严谨的一线教师评卷助手。请基于标准答案对学生作答评分。\n");
        prompt.append("必须只返回一个 JSON 对象，不要返回任何额外文本、解释、Markdown。\n");
        prompt.append("JSON结构必须严格包含以下字段：\n");
        prompt.append("{\n");
        prompt.append("  \"totalScore\": \"得分/总分\",\n");
        prompt.append("  \"overallComment\": \"总评，<=150字\",\n");
        prompt.append("  \"itemScores\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"questionNo\": 1,\n");
        prompt.append("      \"score\": \"x/y\",\n");
        prompt.append("      \"comment\": \"该题评分说明，<=60字\",\n");
        prompt.append("      \"isCorrect\": true\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"weakPoints\": [\"薄弱点1\", \"薄弱点2\"],\n");
        prompt.append("  \"suggestions\": [\"建议1\", \"建议2\"]\n");
        prompt.append("}\n");
        prompt.append("要求：\n");
        prompt.append("1) totalScore 必须是 \"得分/总分\" 格式。\n");
        prompt.append("2) itemScores 至少1项，questionNo 从1递增。\n");
        prompt.append("3) weakPoints 和 suggestions 为字符串数组，可为空数组。\n");
        prompt.append("4) 严禁输出 JSON 以外的内容。\n\n");
        prompt.append("题目标题：").append(assignment.getTitle()).append("\n\n");
        prompt.append("题目内容与参考答案：\n").append(assignment.getContent()).append("\n\n");
        prompt.append("学生答案：\n").append(studentAnswer).append("\n");
        return prompt.toString();
    }

    private EvaluationResult parseEvaluation(String aiResponse) throws Exception {
        String jsonText = extractJson(aiResponse);
        if (jsonText == null || jsonText.isBlank()) {
            return fallbackEvaluation(aiResponse);
        }

        JsonNode root = objectMapper.readTree(jsonText);
        if (!root.isObject()) {
            return fallbackEvaluation(aiResponse);
        }

        JsonNode totalScoreNode = root.get("totalScore");
        JsonNode overallCommentNode = root.get("overallComment");
        JsonNode itemScoresNode = root.get("itemScores");
        if (totalScoreNode == null || overallCommentNode == null || itemScoresNode == null || !itemScoresNode.isArray()) {
            return fallbackEvaluation(aiResponse);
        }

        String totalScore = totalScoreNode.asText("");
        if (!totalScore.matches("^\\s*\\d+(\\.\\d+)?\\s*/\\s*\\d+(\\.\\d+)?\\s*$")) {
            totalScore = "待评";
        }

        String analysis = buildAnalysisFromStructured(root);
        String normalizedJson = objectMapper.writeValueAsString(root);
        return new EvaluationResult(totalScore, analysis, normalizedJson);
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher jsonBlockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (jsonBlockMatcher.find()) {
            return jsonBlockMatcher.group(1);
        }

        Matcher rawJsonMatcher = RAW_JSON_PATTERN.matcher(text.trim());
        if (rawJsonMatcher.find()) {
            return rawJsonMatcher.group(1);
        }
        return null;
    }

    private String buildAnalysisFromStructured(JsonNode root) {
        StringBuilder analysis = new StringBuilder();
        String overallComment = root.path("overallComment").asText("");
        if (!overallComment.isBlank()) {
            analysis.append("总评：").append(overallComment);
        }

        JsonNode itemScores = root.path("itemScores");
        if (itemScores.isArray() && itemScores.size() > 0) {
            if (analysis.length() > 0) analysis.append("\n");
            analysis.append("分题评分：");
            for (JsonNode item : itemScores) {
                int no = item.path("questionNo").asInt(0);
                String score = item.path("score").asText("-");
                String comment = item.path("comment").asText("");
                analysis.append("\n").append(no).append(") ").append(score);
                if (!comment.isBlank()) {
                    analysis.append(" - ").append(comment);
                }
            }
        }

        JsonNode weakPoints = root.path("weakPoints");
        if (weakPoints.isArray() && weakPoints.size() > 0) {
            if (analysis.length() > 0) analysis.append("\n");
            analysis.append("薄弱点：");
            for (int i = 0; i < weakPoints.size(); i++) {
                if (i > 0) analysis.append("；");
                analysis.append(weakPoints.get(i).asText(""));
            }
        }

        JsonNode suggestions = root.path("suggestions");
        if (suggestions.isArray() && suggestions.size() > 0) {
            if (analysis.length() > 0) analysis.append("\n");
            analysis.append("建议：");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) analysis.append("；");
                analysis.append(suggestions.get(i).asText(""));
            }
        }

        return analysis.length() == 0 ? "模型已返回结果，但结构化分析为空" : analysis.toString();
    }

    private EvaluationResult fallbackEvaluation(String aiResponse) {
        return new EvaluationResult("待评", aiResponse, null);
    }

    private record EvaluationResult(String score, String analysis, String evaluationJson) {
    }
}
