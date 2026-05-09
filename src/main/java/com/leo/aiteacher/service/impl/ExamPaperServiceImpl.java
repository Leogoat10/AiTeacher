package com.leo.aiteacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.client.DeepSeekChatClient;
import com.leo.aiteacher.pojo.dto.ConversationDto;
import com.leo.aiteacher.pojo.dto.ExamPaperDto;
import com.leo.aiteacher.pojo.dto.ExamPaperPromptPresetDto;
import com.leo.aiteacher.pojo.dto.ExamPaperTaskDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.ConversationMapper;
import com.leo.aiteacher.pojo.mapper.ExamPaperMapper;
import com.leo.aiteacher.pojo.mapper.ExamPaperPromptPresetMapper;
import com.leo.aiteacher.pojo.mapper.ExamPaperTaskMapper;
import com.leo.aiteacher.service.ExamPaperService;
import com.leo.aiteacher.util.SessionUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExamPaperServiceImpl implements ExamPaperService {

    private static final Logger logger = LoggerFactory.getLogger(ExamPaperServiceImpl.class);
    private static final int DEFAULT_DURATION_MINUTES = 90;
    private static final int DEFAULT_TOTAL_SCORE = 100;
    private static final int DEFAULT_QUESTION_COUNT = 10;
    private static final String DEFAULT_EXAM_TYPE = "单元测验";
    private static final String DEFAULT_DIFFICULTY = "中等";
    private static final int DEFAULT_CONTEXT_ROUNDS = 5;

    @Resource
    private ExamPaperTaskMapper examPaperTaskMapper;

    @Resource
    private ExamPaperMapper examPaperMapper;

    @Resource
    private ExamPaperPromptPresetMapper examPaperPromptPresetMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    @Qualifier("examPaperExecutor")
    private Executor examPaperExecutor;

    @Resource
    private DeepSeekChatClient deepSeekChatClient;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean examPaperTaskSchemaChecked = new AtomicBoolean(false);
    private final AtomicBoolean examPaperPromptPresetSchemaChecked = new AtomicBoolean(false);

    @Override
    public Map<String, Object> createConversation() {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        List<ConversationDto> existingConversations = conversationMapper.getConversationsByTeacherId(teacher.getTeacherId());
        for (ConversationDto conversation : existingConversations) {
            if (!"试卷会话".equals(conversation.getTitle())) {
                continue;
            }
            Long count = examPaperTaskMapper.selectCount(
                    new QueryWrapper<ExamPaperTaskDto>().eq("conversation_id", conversation.getId())
            );
            if (count == null || count == 0L) {
                result.put("success", true);
                result.put("conversationId", conversation.getId());
                return result;
            }
        }

        ConversationDto conversation = new ConversationDto();
        conversation.setTeacherId(teacher.getTeacherId());
        conversation.setTitle("试卷会话");
        conversationMapper.insertConversation(conversation);
        result.put("success", true);
        result.put("conversationId", conversation.getId());
        return result;
    }

    @Override
    public Map<String, Object> getConversations() {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        List<ExamPaperTaskDto> tasks = examPaperTaskMapper.selectList(
                new QueryWrapper<ExamPaperTaskDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .orderByDesc("id")
        );

        java.util.LinkedHashMap<Integer, Map<String, Object>> conversationMap = new java.util.LinkedHashMap<>();
        for (ExamPaperTaskDto task : tasks) {
            Integer conversationId = task.getConversationId();
            if (conversationId == null || conversationMap.containsKey(conversationId)) {
                continue;
            }
            ConversationDto conversation = conversationMapper.getConversationById(conversationId);
            if (conversation == null || !conversation.getTeacherId().equals(teacher.getTeacherId())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", conversationId);
            item.put("title", conversation.getTitle());
            item.put("createTime", task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt());
            conversationMap.put(conversationId, item);
        }

        result.put("success", true);
        result.put("conversations", new java.util.ArrayList<>(conversationMap.values()));
        return result;
    }

    @Override
    public Map<String, Object> deleteConversation(Integer conversationId) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }
        if (conversationId == null) {
            result.put("success", false);
            result.put("error", "会话ID不能为空");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        ConversationDto conversation = conversationMapper.getConversationById(conversationId);
        if (conversation == null) {
            result.put("success", false);
            result.put("error", "对话不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限删除该对话");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        conversationMapper.deleteConversationById(conversationId);
        result.put("success", true);
        return result;
    }

    @Override
    public Map<String, Object> getConversationDetail(Integer conversationId) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        ConversationDto conversation = conversationMapper.getConversationById(conversationId);
        if (conversation == null) {
            result.put("success", false);
            result.put("error", "对话不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限访问该对话");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        List<ExamPaperTaskDto> tasks = examPaperTaskMapper.selectList(
                new QueryWrapper<ExamPaperTaskDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .eq("conversation_id", conversationId)
                        .orderByAsc("id")
        );

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        for (ExamPaperTaskDto task : tasks) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("role", "user");
            userMap.put("content", buildTaskBrief(task));
            userMap.put("timestamp", task.getCreatedAt());
            messages.add(userMap);

            Map<String, Object> aiMap = new HashMap<>();
            aiMap.put("role", "ai");
            aiMap.put("timestamp", task.getCompletedAt() == null ? task.getUpdatedAt() : task.getCompletedAt());
            aiMap.put("contextUsed", Boolean.TRUE.equals(task.getContextUsed()));
            aiMap.put("contextRounds", task.getContextRounds());
            if ("SUCCESS".equals(task.getStatus())) {
                ExamPaperDto paper = examPaperMapper.selectOne(
                        new QueryWrapper<ExamPaperDto>().eq("task_id", task.getId()).last("LIMIT 1")
                );
                aiMap.put("content", paper == null ? "试卷已生成，但未找到展示内容" : paper.getMarkdownContent());
            } else if ("FAILED".equals(task.getStatus())) {
                aiMap.put("content", "生成失败：" + (task.getErrorMessage() == null ? "未知错误" : task.getErrorMessage()));
            } else {
                aiMap.put("content", "任务状态：" + task.getStatus());
            }
            messages.add(aiMap);
        }

        result.put("success", true);
        result.put("messages", messages);
        return result;
    }

    @Override
    public Map<String, Object> listPresetPrompts() {
        ensureExamPaperPromptPresetSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        List<ExamPaperPromptPresetDto> presets = examPaperPromptPresetMapper.selectList(
                new QueryWrapper<ExamPaperPromptPresetDto>()
                        .and(wrapper -> wrapper.eq("is_system_default", 1).or().eq("teacher_id", teacher.getTeacherId()))
                        .orderByDesc("is_system_default")
                        .orderByAsc("id")
        );

        List<Map<String, Object>> items = presets.stream().map(preset -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", preset.getId());
            item.put("title", preset.getTitle());
            item.put("promptContent", preset.getPromptContent());
            item.put("systemDefault", Boolean.TRUE.equals(preset.getSystemDefault()));
            item.put("teacherId", preset.getTeacherId());
            item.put("createdAt", preset.getCreatedAt());
            return item;
        }).toList();

        result.put("success", true);
        result.put("items", items);
        return result;
    }

    @Override
    public Map<String, Object> createPresetPrompt(String title, String promptContent) {
        ensureExamPaperPromptPresetSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        String normalizedTitle = normalizeText(title);
        String normalizedPromptContent = normalizeText(promptContent);
        if (isBlank(normalizedTitle)) {
            result.put("success", false);
            result.put("error", "预设名称不能为空");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }
        if (normalizedTitle.length() > 100) {
            result.put("success", false);
            result.put("error", "预设名称长度不能超过100个字符");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }
        if (isBlank(normalizedPromptContent)) {
            result.put("success", false);
            result.put("error", "预设内容不能为空");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }
        if (normalizedPromptContent.length() > 3000) {
            result.put("success", false);
            result.put("error", "预设内容长度不能超过3000个字符");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        ExamPaperPromptPresetDto preset = new ExamPaperPromptPresetDto();
        preset.setTeacherId(teacher.getTeacherId());
        preset.setTitle(normalizedTitle);
        preset.setPromptContent(normalizedPromptContent);
        preset.setSystemDefault(false);
        examPaperPromptPresetMapper.insert(preset);

        result.put("success", true);
        result.put("id", preset.getId());
        return result;
    }

    @Override
    public Map<String, Object> deletePresetPrompt(Long presetId) {
        ensureExamPaperPromptPresetSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }
        if (presetId == null) {
            result.put("success", false);
            result.put("error", "预设ID不能为空");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        ExamPaperPromptPresetDto preset = examPaperPromptPresetMapper.selectById(presetId);
        if (preset == null) {
            result.put("success", false);
            result.put("error", "预设不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (Boolean.TRUE.equals(preset.getSystemDefault())) {
            result.put("success", false);
            result.put("error", "系统默认预设不支持删除");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }
        if (!teacher.getTeacherId().equals(preset.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限删除该预设");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        examPaperPromptPresetMapper.deleteById(presetId);
        result.put("success", true);
        return result;
    }

    @Override
    public Map<String, Object> createExamPaperTask(String subject, String grade, String examType, String textbookVersion,
                                                   Integer durationMinutes, Integer totalScore,
                                                   Integer questionCount, Map<String, Integer> questionTypeCounts, String difficulty,
                                                   String knowledgePoints, String customRequirement, Integer conversationId,
                                                   Boolean useContext, Integer contextRounds) {
        ensureExamPaperTaskSchema();
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
            ConversationDto conversation = new ConversationDto();
            conversation.setTeacherId(teacher.getTeacherId());
            conversation.setTitle("试卷会话");
            conversationMapper.insertConversation(conversation);
            actualConversationId = conversation.getId();
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

        String normalizedSubject = normalizeText(subject);
        String normalizedGrade = normalizeText(grade);
        String normalizedExamType = normalizeText(examType);
        String normalizedTextbookVersion = normalizeText(textbookVersion);
        String normalizedKnowledgePoints = normalizeText(knowledgePoints);
        String normalizedRequirement = normalizeText(customRequirement);
        String normalizedDifficulty = resolveDifficulty(isBlank(difficulty) ? normalizedRequirement : normalizeText(difficulty));

        int normalizedDuration = durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
        int normalizedTotalScore = totalScore == null ? DEFAULT_TOTAL_SCORE : totalScore;
        int normalizedQuestionCount = questionCount == null ? DEFAULT_QUESTION_COUNT : questionCount;
        Map<String, Integer> normalizedQuestionTypeCounts = normalizeQuestionTypeCounts(questionTypeCounts);

        if (enableContext) {
            ExamPaperTaskDto latestTask = findLatestTask(teacher.getTeacherId(), actualConversationId);
            if (latestTask != null) {
                if (isBlank(normalizedSubject)) normalizedSubject = latestTask.getSubject();
                if (isBlank(normalizedGrade)) normalizedGrade = latestTask.getGrade();
                if (isBlank(normalizedExamType)) normalizedExamType = latestTask.getExamType();
                if (durationMinutes == null && latestTask.getDurationMinutes() != null) {
                    normalizedDuration = latestTask.getDurationMinutes();
                }
                if (totalScore == null && latestTask.getTotalScore() != null) {
                    normalizedTotalScore = latestTask.getTotalScore();
                }
                if (isBlank(normalizedDifficulty)) normalizedDifficulty = latestTask.getDifficulty();
                if (isBlank(normalizedKnowledgePoints)) normalizedKnowledgePoints = latestTask.getKnowledgePoints();
                if (questionCount == null && latestTask.getQuestionCount() != null) {
                    normalizedQuestionCount = latestTask.getQuestionCount();
                }
                if (totalQuestionCount(normalizedQuestionTypeCounts) <= 0 && latestTask.getResultJson() != null && !latestTask.getResultJson().isBlank()) {
                    normalizedQuestionTypeCounts = buildQuestionTypeCountsFromResult(latestTask.getResultJson());
                }
            }
        }

        int countedTotal = totalQuestionCount(normalizedQuestionTypeCounts);
        if (countedTotal > 0) {
            normalizedQuestionCount = countedTotal;
        }

        if (isBlank(normalizedExamType)) {
            normalizedExamType = DEFAULT_EXAM_TYPE;
        }
        String validationError = validateRequest(
                normalizedSubject, normalizedGrade, normalizedExamType, normalizedTextbookVersion,
                normalizedDuration, normalizedTotalScore, normalizedQuestionCount, normalizedQuestionTypeCounts
        );
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

        String title = buildTitleFromForm(normalizedSubject, normalizedGrade, normalizedExamType, normalizedRequirement);
        if (isNewConversation || shouldRefreshConversationTitle(existingConversation)) {
            ConversationDto toUpdate = new ConversationDto();
            toUpdate.setId(actualConversationId);
            toUpdate.setTitle(title);
            conversationMapper.updateById(toUpdate);
        }

        String prompt = buildPrompt(
                normalizedSubject, normalizedGrade, normalizedExamType, normalizedTextbookVersion,
                normalizedDuration, normalizedTotalScore, normalizedQuestionCount,
                normalizedQuestionTypeCounts, normalizedDifficulty, normalizedKnowledgePoints, normalizedRequirement,
                enableContext, actualContextRounds, recentContextSummary
        );

        ExamPaperTaskDto task = new ExamPaperTaskDto();
        task.setTeacherId(teacher.getTeacherId());
        task.setConversationId(actualConversationId);
        task.setStatus("PENDING");
        task.setSubject(normalizedSubject);
        task.setGrade(normalizedGrade);
        task.setExamType(normalizedExamType);
        task.setDurationMinutes(normalizedDuration);
        task.setTotalScore(normalizedTotalScore);
        task.setQuestionCount(normalizedQuestionCount);
        task.setContextUsed(enableContext);
        task.setContextRounds(actualContextRounds);
        task.setDifficulty(normalizedDifficulty);
        task.setKnowledgePoints(normalizedKnowledgePoints);
        task.setCustomRequirement(normalizedRequirement);
        task.setRequestPrompt(prompt);
        examPaperTaskMapper.insert(task);

        examPaperExecutor.execute(() -> executeTask(task.getId()));

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

    @Override
    public Map<String, Object> getExamPaperTaskStatus(Long taskId) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        ExamPaperTaskDto task = examPaperTaskMapper.selectById(taskId);
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
        result.put("useContext", Boolean.TRUE.equals(task.getContextUsed()));
        result.put("contextRounds", task.getContextRounds());
        result.put("errorMessage", task.getErrorMessage());
        result.put("createdAt", task.getCreatedAt());
        result.put("updatedAt", task.getUpdatedAt());
        result.put("completedAt", task.getCompletedAt());

        ExamPaperDto paper = examPaperMapper.selectOne(
                new QueryWrapper<ExamPaperDto>().eq("task_id", task.getId()).last("LIMIT 1")
        );
        if (paper != null) {
            result.put("paperId", paper.getId());
        }
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                result.put("result", objectMapper.readValue(task.getResultJson(), Map.class));
            } catch (Exception e) {
                result.put("result", task.getResultJson());
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> listExamPapers(Integer page, Integer pageSize) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        int pageNo = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        long total = examPaperMapper.selectCount(
                new QueryWrapper<ExamPaperDto>().eq("teacher_id", teacher.getTeacherId())
        );
        int offset = (pageNo - 1) * size;
        List<ExamPaperDto> records = examPaperMapper.selectList(
                new QueryWrapper<ExamPaperDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .orderByDesc("id")
                        .last("LIMIT " + offset + "," + size)
        );

        List<Map<String, Object>> items = records.stream().map(paper -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", paper.getId());
            item.put("taskId", paper.getTaskId());
            item.put("title", paper.getTitle());
            item.put("subject", paper.getSubject());
            item.put("grade", paper.getGrade());
            item.put("examType", paper.getExamType());
            item.put("durationMinutes", paper.getDurationMinutes());
            item.put("totalScore", paper.getTotalScore());
            item.put("questionCount", paper.getQuestionCount());
            item.put("difficulty", paper.getDifficulty());
            item.put("createdAt", paper.getCreatedAt());
            return item;
        }).toList();

        result.put("success", true);
        result.put("items", items);
        result.put("page", pageNo);
        result.put("pageSize", size);
        result.put("total", total);
        return result;
    }

    @Override
    public Map<String, Object> getExamPaperDetail(Long paperId) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        ExamPaperDto paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            result.put("success", false);
            result.put("error", "试卷不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!paper.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限访问该试卷");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        result.put("success", true);
        result.put("id", paper.getId());
        result.put("taskId", paper.getTaskId());
        result.put("title", paper.getTitle());
        result.put("subject", paper.getSubject());
        result.put("grade", paper.getGrade());
        result.put("examType", paper.getExamType());
        result.put("durationMinutes", paper.getDurationMinutes());
        result.put("totalScore", paper.getTotalScore());
        result.put("questionCount", paper.getQuestionCount());
        result.put("difficulty", paper.getDifficulty());
        result.put("knowledgePoints", paper.getKnowledgePoints());
        result.put("summary", paper.getSummary());
        result.put("structure", parseJsonNodeField(paper.getStructureJson()));
        result.put("markdownContent", paper.getMarkdownContent());
        result.put("createdAt", paper.getCreatedAt());
        result.put("updatedAt", paper.getUpdatedAt());
        return result;
    }

    @Override
    public Map<String, Object> deleteExamPaper(Long paperId) {
        ensureExamPaperTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        ExamPaperDto paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            result.put("success", false);
            result.put("error", "试卷不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!paper.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限删除该试卷");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        examPaperMapper.deleteById(paperId);
        result.put("success", true);
        return result;
    }

    private void executeTask(Long taskId) {
        ensureExamPaperTaskSchema();
        ExamPaperTaskDto task = examPaperTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        try {
            task.setStatus("RUNNING");
            task.setUpdatedAt(LocalDateTime.now());
            examPaperTaskMapper.updateById(task);

            DeepSeekChatClient.ChatResult chatResult = deepSeekChatClient.chat(task.getRequestPrompt());
            ParsedExamPaper parsed = parseAndValidate(chatResult.content(), task.getQuestionCount(), task.getTotalScore());

            ExamPaperDto paper = new ExamPaperDto();
            paper.setTaskId(task.getId());
            paper.setTeacherId(task.getTeacherId());
            paper.setTitle(parsed.title());
            paper.setSubject(task.getSubject());
            paper.setGrade(task.getGrade());
            paper.setExamType(task.getExamType());
            paper.setDurationMinutes(task.getDurationMinutes());
            paper.setTotalScore(task.getTotalScore());
            paper.setQuestionCount(task.getQuestionCount());
            paper.setDifficulty(task.getDifficulty());
            paper.setKnowledgePoints(task.getKnowledgePoints());
            paper.setSummary(parsed.summary());
            paper.setStructureJson(parsed.resultJson());
            paper.setMarkdownContent(buildMarkdown(task, parsed));
            examPaperMapper.insert(paper);

            task.setStatus("SUCCESS");
            task.setRawResponse(chatResult.rawResponse());
            task.setResultJson(parsed.resultJson());
            task.setErrorMessage(null);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            examPaperTaskMapper.updateById(task);
        } catch (Exception ex) {
            logger.error("试卷任务执行失败, taskId={}", taskId, ex);
            task.setStatus("FAILED");
            task.setErrorMessage("生成失败: " + ex.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            examPaperTaskMapper.updateById(task);
        }
    }

    private ParsedExamPaper parseAndValidate(String content, Integer requiredQuestionCount, Integer requiredTotalScore) throws Exception {
        String normalized = normalizeModelContent(content);
        JsonNode root = objectMapper.readTree(normalized);

        String title = requiredText(root, "title");
        String summary = optionalText(root, "summary");
        JsonNode questions = requiredArray(root, "questions");
        String notes = root.path("notes").isTextual() ? root.path("notes").asText("").trim() : "";

        if (questions.size() < requiredQuestionCount) {
            throw new RuntimeException("题量不足，至少需要 " + requiredQuestionCount + " 题");
        }

        int scoreSum = 0;
        for (JsonNode question : questions) {
            if (!question.path("stem").isTextual() || question.path("stem").asText().isBlank()) {
                throw new RuntimeException("试题缺少 stem 字段");
            }
            if (!question.path("type").isTextual() || question.path("type").asText().isBlank()) {
                throw new RuntimeException("试题缺少 type 字段");
            }
            if (!question.path("answer").isTextual() || question.path("answer").asText().isBlank()) {
                throw new RuntimeException("试题缺少 answer 字段");
            }
            if (!question.path("analysis").isTextual() || question.path("analysis").asText().isBlank()) {
                throw new RuntimeException("试题缺少 analysis 字段");
            }
            if (!question.path("score").isInt() || question.path("score").asInt() <= 0) {
                throw new RuntimeException("试题 score 字段不合法");
            }
            scoreSum += question.path("score").asInt();
        }

        if (Math.abs(scoreSum - requiredTotalScore) > 20) {
            throw new RuntimeException("总分偏差较大，当前总分 " + scoreSum + "，目标总分 " + requiredTotalScore);
        }

        return new ParsedExamPaper(
                title,
                summary,
                notes,
                objectMapper.writeValueAsString(questions),
                objectMapper.writeValueAsString(root)
        );
    }

    private String buildMarkdown(ExamPaperTaskDto task, ParsedExamPaper parsed) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("AI生成\n\n");
        builder.append("# ").append(parsed.title()).append("\n\n");
        builder.append("科目：").append(task.getSubject()).append("\n");
        builder.append("年级：").append(task.getGrade()).append("\n");
        builder.append("试卷类型：").append(task.getExamType()).append("\n");
        builder.append("考试时长：").append(task.getDurationMinutes()).append(" 分钟\n");
        builder.append("总分：").append(task.getTotalScore()).append(" 分\n");
        builder.append("题量：").append(task.getQuestionCount()).append(" 题\n");
        builder.append("难度：").append(task.getDifficulty()).append("\n");
        if (!isBlank(task.getKnowledgePoints())) {
            builder.append("知识点：").append(task.getKnowledgePoints()).append("\n");
        }
        builder.append("\n## 试卷说明\n")
                .append(isBlank(parsed.summary()) ? "根据教学参数自动生成，建议结合班级学情进行微调。" : parsed.summary())
                .append("\n\n");
        builder.append("## 题目部分\n");
        appendQuestions(builder, parsed.questionsJson());
        builder.append("\n## 答案与解析部分\n");
        appendAnswersAndAnalysis(builder, parsed.questionsJson());
        if (!isBlank(parsed.notes())) {
            builder.append("\n## 命题备注\n").append(parsed.notes()).append("\n");
        }
        return builder.toString();
    }

    private void appendQuestions(StringBuilder builder, String questionsJson) throws Exception {
        JsonNode questions = objectMapper.readTree(questionsJson);
        int index = 1;
        for (JsonNode question : questions) {
            builder.append(index++).append(". 【")
                    .append(question.path("type").asText("题目"))
                    .append("，")
                    .append(question.path("score").asInt(0))
                    .append("分】")
                    .append(question.path("stem").asText(""))
                    .append("\n");

            JsonNode options = question.path("options");
            if (options.isArray() && !options.isEmpty()) {
                for (JsonNode option : options) {
                    builder.append("   ").append(option.asText("")).append("\n");
                }
            }
        }
    }

    private void appendAnswersAndAnalysis(StringBuilder builder, String questionsJson) throws Exception {
        JsonNode questions = objectMapper.readTree(questionsJson);
        int index = 1;
        for (JsonNode question : questions) {
            builder.append(index++).append(". ")
                    .append("答案：").append(question.path("answer").asText("")).append("\n")
                    .append("   解析：").append(question.path("analysis").asText("暂无")).append("\n");
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new RuntimeException("缺少字段: " + field);
        }
        return node.asText().trim();
    }

    private String optionalText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isTextual()) {
            return "";
        }
        return node.asText("").trim();
    }

    private JsonNode requiredArray(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray() || node.isEmpty()) {
            throw new RuntimeException("缺少数组字段: " + field);
        }
        return node;
    }

    private String normalizeModelContent(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json\\s*", "");
            trimmed = trimmed.replaceFirst("^```\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
    }

    private Object parseJsonNodeField(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildPrompt(String subject, String grade, String examType, String textbookVersion, int durationMinutes,
                               int totalScore, int questionCount, Map<String, Integer> questionTypeCounts, String difficulty,
                               String knowledgePoints, String customRequirement, boolean useContext,
                               int contextRounds, String recentContextSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【角色设定】\n")
                .append("你是一位资深命题专家，擅长根据学段目标与课程标准设计高质量试卷。\n\n")
                .append("【任务目标】\n")
                .append("请生成一份完整试卷，要求题目结构清晰、梯度合理、可直接用于课堂测评。\n\n")
                .append("【试卷参数】\n")
                .append("- 科目：").append(subject).append("\n")
                .append("- 年级：").append(grade).append("\n")
                .append("- 试卷类型：").append(examType).append("\n")
                .append("- 教材版本：").append(isBlank(textbookVersion) ? "未指定（请结合当前会话历史与该学段常用版本合理生成）" : textbookVersion).append("\n")
                .append("- 考试时长：").append(durationMinutes).append(" 分钟\n")
                .append("- 总分：").append(totalScore).append(" 分\n")
                .append("- 题量：").append(questionCount).append(" 题\n")
                .append("- 难度：").append(difficulty).append("\n");
        prompt.append("- 题型数量：选择题").append(questionTypeCounts.getOrDefault("选择题", 0))
                .append("，填空题").append(questionTypeCounts.getOrDefault("填空题", 0))
                .append("，判断题").append(questionTypeCounts.getOrDefault("判断题", 0))
                .append("，简答题").append(questionTypeCounts.getOrDefault("简答题", 0))
                .append("，解答题").append(questionTypeCounts.getOrDefault("解答题", 0)).append("\n");

        if (!isBlank(knowledgePoints)) {
            prompt.append("- 重点知识点：").append(knowledgePoints).append("\n");
        } else {
            prompt.append("- 重点知识点：未指定，请根据学段核心内容均衡覆盖\n");
        }

        if (!isBlank(customRequirement)) {
            prompt.append("- 教师补充要求：").append(customRequirement).append("\n");
        } else {
            prompt.append("- 教师补充要求：无\n");
        }

        prompt.append("\n【历史上下文】\n");
        if (!useContext) {
            prompt.append("本轮未启用历史上下文，请按本轮参数独立生成。\n");
        } else {
            prompt.append("本轮已启用历史上下文，最多关联最近").append(contextRounds).append("轮。\n");
            prompt.append(recentContextSummary).append("\n");
        }

        prompt.append("\n【命题要求】\n")
                .append("1) 题目覆盖基础、提升、综合层次，难度分布合理。\n")
                .append("2) 每题必须提供标准答案与解析。\n")
                .append("3) 如为选择题，可提供 options 数组；非选择题 options 可为空数组。\n")
                .append("4) 所有题目 score 相加应接近 ").append(totalScore).append(" 分，且至少 ").append(questionCount).append(" 题。\n")
                .append("5) 题干和解析可包含 LaTeX 数学公式。\n\n")
                .append("【输出格式约束】\n")
                .append("请严格只输出 JSON，不要包含任何解释，不要使用 Markdown 代码块。\n")
                .append("字段名必须完全一致，结构如下：\n")
                .append("{\n")
                .append("  \"title\": \"试卷标题\",\n")
                .append("  \"summary\": \"命题说明与覆盖范围\",\n")
                .append("  \"questions\": [\n")
                .append("    {\n")
                .append("      \"type\": \"选择题/填空题/解答题等\",\n")
                .append("      \"stem\": \"题干\",\n")
                .append("      \"options\": [\"A. ...\", \"B. ...\"],\n")
                .append("      \"answer\": \"标准答案\",\n")
                .append("      \"analysis\": \"解析\",\n")
                .append("      \"score\": 5\n")
                .append("    }\n")
                .append("  ],\n")
                .append("  \"notes\": \"可选：命题人备注\"\n")
                .append("}\n");
        prompt.append("请严格按照题型数量输出：选择题").append(questionTypeCounts.getOrDefault("选择题", 0))
                .append("题、填空题").append(questionTypeCounts.getOrDefault("填空题", 0))
                .append("题、判断题").append(questionTypeCounts.getOrDefault("判断题", 0))
                .append("题、简答题").append(questionTypeCounts.getOrDefault("简答题", 0))
                .append("题、解答题").append(questionTypeCounts.getOrDefault("解答题", 0)).append("题。\n");
        return prompt.toString();
    }

    private String validateRequest(String subject, String grade, String examType, String textbookVersion,
                                   int durationMinutes, int totalScore, int questionCount,
                                   Map<String, Integer> questionTypeCounts) {
        if (isBlank(subject)) {
            return "科目不能为空";
        }
        if (isBlank(grade)) {
            return "年级不能为空";
        }
        if (isBlank(examType)) {
            return "试卷类型不能为空";
        }
        if (durationMinutes < 30 || durationMinutes > 180) {
            return "考试时长需在30-180分钟之间";
        }
        if (totalScore < 20 || totalScore > 200) {
            return "总分需在20-200之间";
        }
        if (questionCount < 1 || questionCount > 60) {
            return "题量需在1-60之间";
        }
        if (totalQuestionCount(questionTypeCounts) <= 0) {
            return "请至少设置一种题型数量";
        }
        return null;
    }

    private String resolveDifficulty(String customRequirement) {
        String fromCustom = detectDifficultyFromText(customRequirement);
        if (!isBlank(fromCustom)) {
            return fromCustom;
        }
        return DEFAULT_DIFFICULTY;
    }

    private String detectDifficultyFromText(String text) {
        if (isBlank(text)) {
            return null;
        }
        String normalized = text.replace(" ", "");
        if (normalized.contains("简单") || normalized.contains("更简单") || normalized.contains("降低难度")) {
            return "简单";
        }
        if (normalized.contains("困难") || normalized.contains("较难") || normalized.contains("更难") || normalized.contains("提高难度")) {
            return "较难";
        }
        if (normalized.contains("中等") || normalized.contains("适中")) {
            return "中等";
        }
        return null;
    }

    private ExamPaperTaskDto findLatestTask(Integer teacherId, Integer conversationId) {
        return examPaperTaskMapper.selectOne(
                new QueryWrapper<ExamPaperTaskDto>()
                        .eq("teacher_id", teacherId)
                        .eq("conversation_id", conversationId)
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );
    }

    private int normalizeContextRounds(Integer contextRounds) {
        if (contextRounds == null) {
            return DEFAULT_CONTEXT_ROUNDS;
        }
        if (contextRounds < 1) {
            return 1;
        }
        return Math.min(contextRounds, DEFAULT_CONTEXT_ROUNDS);
    }

    private String buildRecentContextSummary(Integer conversationId, int rounds) {
        List<ExamPaperTaskDto> taskRounds = examPaperTaskMapper.selectList(
                new QueryWrapper<ExamPaperTaskDto>()
                        .eq("conversation_id", conversationId)
                        .eq("status", "SUCCESS")
                        .orderByDesc("id")
                        .last("LIMIT " + rounds)
        );
        if (taskRounds == null || taskRounds.isEmpty()) {
            return "";
        }

        List<ExamPaperTaskDto> recentRounds = new java.util.ArrayList<>(taskRounds);
        java.util.Collections.reverse(recentRounds);
        ExamPaperTaskDto latest = recentRounds.get(recentRounds.size() - 1);

        StringBuilder summary = new StringBuilder();
        summary.append("【最近一轮试卷基准】\n");
        summary.append("- 用户请求：").append(abbreviate(buildTaskBrief(latest), 200)).append("\n");
        summary.append("- 试卷摘要：").append(abbreviate(extractTaskSummary(latest), 1200)).append("\n\n");
        summary.append("【历史轮次摘要】\n");
        for (int i = 0; i < recentRounds.size(); i++) {
            ExamPaperTaskDto task = recentRounds.get(i);
            summary.append("Round ").append(i + 1).append(":\n");
            summary.append("- 用户意图摘要：").append(abbreviate(buildTaskBrief(task), 180)).append("\n");
            summary.append("- AI产出摘要：").append(abbreviate(extractTaskSummary(task), 500)).append("\n");
        }
        return summary.toString().trim();
    }

    private String buildTaskBrief(ExamPaperTaskDto task) {
        StringBuilder brief = new StringBuilder();
        brief.append(task.getSubject()).append(" ").append(task.getGrade()).append(" ").append(task.getExamType());
        brief.append(" (").append(task.getDurationMinutes()).append("分钟, ");
        brief.append(task.getTotalScore()).append("分, ").append(task.getQuestionCount()).append("题)");
        if (task.getCustomRequirement() != null && !task.getCustomRequirement().isBlank()) {
            brief.append(" - ").append(task.getCustomRequirement());
        }
        return brief.toString();
    }

    private String extractTaskSummary(ExamPaperTaskDto task) {
        if (task.getResultJson() == null || task.getResultJson().isBlank()) {
            return buildTaskBrief(task);
        }
        try {
            JsonNode root = objectMapper.readTree(task.getResultJson());
            StringBuilder summary = new StringBuilder();
            summary.append("标题：").append(root.path("title").asText("")).append("；");
            summary.append("概述：").append(abbreviate(root.path("summary").asText(""), 120));
            JsonNode questions = root.path("questions");
            if (questions.isArray()) {
                summary.append("；题量：").append(questions.size());
            }
            return summary.toString();
        } catch (Exception ignore) {
            return buildTaskBrief(task);
        }
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

    private String buildTitleFromForm(String subject, String grade, String examType, String customRequirement) {
        StringBuilder title = new StringBuilder();
        title.append(subject).append(" ").append(grade).append(" ").append(examType);
        if (customRequirement != null && !customRequirement.isBlank()) {
            String customPart = customRequirement.length() > 20
                    ? customRequirement.substring(0, 20) + "..."
                    : customRequirement;
            title.append(" - ").append(customPart);
        }
        return title.toString();
    }

    private boolean shouldRefreshConversationTitle(ConversationDto conversation) {
        if (conversation == null) {
            return false;
        }
        String title = conversation.getTitle();
        return title == null
                || title.isBlank()
                || "请发送消息".equals(title.trim())
                || "试卷会话".equals(title.trim());
    }

    private Map<String, Integer> buildQuestionTypeCountsFromResult(String resultJson) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        counts.put("选择题", 0);
        counts.put("填空题", 0);
        counts.put("判断题", 0);
        counts.put("简答题", 0);
        counts.put("解答题", 0);
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode questions = root.path("questions");
            if (!questions.isArray()) {
                return counts;
            }
            for (JsonNode question : questions) {
                String type = question.path("type").asText("");
                if (counts.containsKey(type)) {
                    counts.put(type, counts.get(type) + 1);
                }
            }
        } catch (Exception ignore) {
            return counts;
        }
        return counts;
    }

    private Map<String, Integer> normalizeQuestionTypeCounts(Map<String, Integer> raw) {
        java.util.LinkedHashMap<String, Integer> normalized = new java.util.LinkedHashMap<>();
        normalized.put("选择题", 0);
        normalized.put("填空题", 0);
        normalized.put("判断题", 0);
        normalized.put("简答题", 0);
        normalized.put("解答题", 0);
        if (raw == null || raw.isEmpty()) {
            return normalized;
        }
        for (String key : normalized.keySet()) {
            Integer value = raw.get(key);
            normalized.put(key, Math.max(0, value == null ? 0 : value));
        }
        return normalized;
    }

    private int totalQuestionCount(Map<String, Integer> counts) {
        if (counts == null) {
            return 0;
        }
        int total = 0;
        for (Integer value : counts.values()) {
            total += Math.max(0, value == null ? 0 : value);
        }
        return total;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureExamPaperPromptPresetSchema() {
        if (examPaperPromptPresetSchemaChecked.get()) {
            return;
        }
        synchronized (examPaperPromptPresetSchemaChecked) {
            if (examPaperPromptPresetSchemaChecked.get()) {
                return;
            }
            try {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS exam_paper_prompt_presets (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            teacher_id INT NULL COMMENT '创建教师ID，系统默认预设为空',
                            title VARCHAR(100) NOT NULL COMMENT '预设名称',
                            prompt_content TEXT NOT NULL COMMENT '预设Prompt内容',
                            is_system_default TINYINT(1) DEFAULT 0 NOT NULL COMMENT '是否系统默认预设',
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                            KEY idx_eppp_teacher (teacher_id),
                            KEY idx_eppp_system_default (is_system_default),
                            CONSTRAINT fk_eppp_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                seedSystemPresetPrompt("公式表达加强版", "若题目涉及数学表达，请优先使用 LaTeX 形式输出关键公式，并保证变量定义清晰。");
                seedSystemPresetPrompt("分层梯度优化版", "题目难度按基础:提升:综合约 5:3:2 分配，每类题目都要有代表性。");
                seedSystemPresetPrompt("情境应用导向版", "优先设计贴近真实生活或学科情境的问题，提高迁移与应用能力考查。");
                seedSystemPresetPrompt("错因诊断强化版", "解析中要指出常见错误思路，并给出针对性纠正建议。");
                examPaperPromptPresetSchemaChecked.set(true);
            } catch (Exception ex) {
                logger.error("自动补齐 exam_paper_prompt_presets 表结构失败", ex);
                throw new RuntimeException("exam_paper_prompt_presets 表结构缺失，且自动迁移失败，请手动执行 aiTeacher.sql 中相关建表语句");
            }
        }
    }

    private void seedSystemPresetPrompt(String title, String content) {
        jdbcTemplate.update(
                """
                        INSERT INTO exam_paper_prompt_presets (teacher_id, title, prompt_content, is_system_default)
                        SELECT NULL, ?, ?, 1
                        FROM DUAL
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM exam_paper_prompt_presets
                            WHERE is_system_default = 1
                              AND title = ?
                        )
                        """,
                title, content, title
        );
    }

    private void ensureExamPaperTaskSchema() {
        if (examPaperTaskSchemaChecked.get()) {
            return;
        }
        synchronized (examPaperTaskSchemaChecked) {
            if (examPaperTaskSchemaChecked.get()) {
                return;
            }
            try {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS exam_paper_tasks (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            teacher_id INT NOT NULL COMMENT '教师ID',
                            conversation_id INT NULL COMMENT '会话ID',
                            status VARCHAR(20) NOT NULL COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED',
                            subject VARCHAR(100) NOT NULL COMMENT '科目',
                            grade VARCHAR(100) NOT NULL COMMENT '年级',
                            exam_type VARCHAR(100) NOT NULL COMMENT '试卷类型',
                            duration_minutes INT NOT NULL COMMENT '考试时长(分钟)',
                            total_score INT NOT NULL COMMENT '总分',
                            question_count INT NOT NULL COMMENT '题量',
                            context_used TINYINT(1) DEFAULT 0 NOT NULL COMMENT '是否启用上下文',
                            context_rounds INT DEFAULT 5 NOT NULL COMMENT '关联上下文轮次',
                            difficulty VARCHAR(50) NOT NULL COMMENT '难度',
                            knowledge_points VARCHAR(500) NULL COMMENT '知识点',
                            custom_requirement TEXT NULL COMMENT '补充要求',
                            request_prompt LONGTEXT NULL COMMENT '生成请求Prompt',
                            raw_response LONGTEXT NULL COMMENT '模型原始响应',
                            result_json LONGTEXT NULL COMMENT '结构化结果(JSON)',
                            error_message VARCHAR(500) NULL COMMENT '失败原因',
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                            completed_at TIMESTAMP NULL,
                            KEY idx_ept_teacher (teacher_id),
                            KEY idx_ept_status (status),
                            KEY idx_ept_conversation (conversation_id),
                            CONSTRAINT fk_ept_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
                            CONSTRAINT fk_ept_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);

                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS exam_papers (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            task_id BIGINT NULL COMMENT '来源任务ID',
                            teacher_id INT NOT NULL COMMENT '教师ID',
                            title VARCHAR(255) NOT NULL COMMENT '试卷标题',
                            subject VARCHAR(100) NOT NULL COMMENT '科目',
                            grade VARCHAR(100) NOT NULL COMMENT '年级',
                            exam_type VARCHAR(100) NOT NULL COMMENT '试卷类型',
                            duration_minutes INT NOT NULL COMMENT '考试时长(分钟)',
                            total_score INT NOT NULL COMMENT '总分',
                            question_count INT NOT NULL COMMENT '题量',
                            difficulty VARCHAR(50) NOT NULL COMMENT '难度',
                            knowledge_points VARCHAR(500) NULL COMMENT '知识点',
                            summary TEXT NULL COMMENT '试卷说明',
                            structure_json LONGTEXT NULL COMMENT '结构化试卷(JSON)',
                            markdown_content LONGTEXT NULL COMMENT '试卷Markdown内容',
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                            KEY idx_ep_teacher (teacher_id),
                            KEY idx_ep_task (task_id),
                            CONSTRAINT fk_ep_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id) ON DELETE CASCADE,
                            CONSTRAINT fk_ep_task FOREIGN KEY (task_id) REFERENCES exam_paper_tasks (id) ON DELETE SET NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);

                ensureColumn("conversation_id", "ALTER TABLE exam_paper_tasks ADD COLUMN conversation_id INT NULL COMMENT '会话ID'");
                ensureColumn("context_used", "ALTER TABLE exam_paper_tasks ADD COLUMN context_used TINYINT(1) DEFAULT 0 NOT NULL COMMENT '是否启用上下文'");
                ensureColumn("context_rounds", "ALTER TABLE exam_paper_tasks ADD COLUMN context_rounds INT DEFAULT 5 NOT NULL COMMENT '关联上下文轮次'");
                examPaperTaskSchemaChecked.set(true);
            } catch (Exception ex) {
                logger.error("自动补齐 exam_paper_tasks/exam_papers 表结构失败", ex);
                throw new RuntimeException("exam_paper_tasks/exam_papers 表结构缺失，且自动迁移失败，请手动执行 aiTeacher.sql 中相关建表语句");
            }
        }
    }

    private void ensureColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam_paper_tasks' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(alterSql);
        logger.info("已自动补齐 exam_paper_tasks.{} 字段", columnName);
    }

    private record ParsedExamPaper(
            String title,
            String summary,
            String notes,
            String questionsJson,
            String resultJson
    ) {
    }
}
