package com.leo.aiteacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.client.DeepSeekChatClient;
import com.leo.aiteacher.pojo.dto.ConversationDto;
import com.leo.aiteacher.pojo.dto.GenerationTaskDto;
import com.leo.aiteacher.pojo.dto.LessonPlanDto;
import com.leo.aiteacher.pojo.dto.LessonPlanPromptPresetDto;
import com.leo.aiteacher.pojo.dto.LessonPlanTaskDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.ConversationMapper;
import com.leo.aiteacher.pojo.mapper.GenerationTaskMapper;
import com.leo.aiteacher.pojo.mapper.LessonPlanMapper;
import com.leo.aiteacher.pojo.mapper.LessonPlanPromptPresetMapper;
import com.leo.aiteacher.pojo.mapper.LessonPlanTaskMapper;
import com.leo.aiteacher.service.LessonPlanService;
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
public class LessonPlanServiceImpl implements LessonPlanService {

    private static final Logger logger = LoggerFactory.getLogger(LessonPlanServiceImpl.class);
    private static final int DEFAULT_DURATION_MINUTES = 45;
    private static final int DEFAULT_INTERACTION_COUNT = 3;

    @Resource
    private LessonPlanTaskMapper lessonPlanTaskMapper;

    @Resource
    private LessonPlanMapper lessonPlanMapper;

    @Resource
    private LessonPlanPromptPresetMapper lessonPlanPromptPresetMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    @Qualifier("lessonPlanExecutor")
    private Executor lessonPlanExecutor;

    @Resource
    private GenerationTaskMapper generationTaskMapper;

    @Resource
    private DeepSeekChatClient deepSeekChatClient;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean lessonPlanTaskSchemaChecked = new AtomicBoolean(false);
    private final AtomicBoolean lessonPlanPromptPresetSchemaChecked = new AtomicBoolean(false);

    @Override
    public Map<String, Object> createConversation() {
        ensureLessonPlanTaskSchema();
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
            if (!"教案会话".equals(conversation.getTitle())) {
                continue;
            }
            if (hasQuestionHistory(teacher.getTeacherId(), conversation.getId())) {
                continue;
            }
            Long count = lessonPlanTaskMapper.selectCount(
                    new QueryWrapper<LessonPlanTaskDto>().eq("conversation_id", conversation.getId())
            );
            if (count == null || count == 0L) {
                result.put("success", true);
                result.put("conversationId", conversation.getId());
                return result;
            }
        }

        ConversationDto conversation = new ConversationDto();
        conversation.setTeacherId(teacher.getTeacherId());
        conversation.setTitle("教案会话");
        conversationMapper.insertConversation(conversation);
        result.put("success", true);
        result.put("conversationId", conversation.getId());
        return result;
    }

    @Override
    public Map<String, Object> getConversations() {
        ensureLessonPlanTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        List<LessonPlanTaskDto> tasks = lessonPlanTaskMapper.selectList(
                new QueryWrapper<LessonPlanTaskDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .orderByDesc("id")
        );

        java.util.LinkedHashMap<Integer, Map<String, Object>> conversationMap = new java.util.LinkedHashMap<>();
        for (LessonPlanTaskDto task : tasks) {
            Integer conversationId = task.getConversationId();
            if (conversationId == null || conversationMap.containsKey(conversationId)) {
                continue;
            }
            if (hasQuestionHistory(teacher.getTeacherId(), conversationId)) {
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
    public Map<String, Object> listPresetPrompts() {
        ensureLessonPlanPromptPresetSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        List<LessonPlanPromptPresetDto> presets = lessonPlanPromptPresetMapper.selectList(
                new QueryWrapper<LessonPlanPromptPresetDto>()
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
        ensureLessonPlanPromptPresetSchema();
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

        LessonPlanPromptPresetDto preset = new LessonPlanPromptPresetDto();
        preset.setTeacherId(teacher.getTeacherId());
        preset.setTitle(normalizedTitle);
        preset.setPromptContent(normalizedPromptContent);
        preset.setSystemDefault(false);
        lessonPlanPromptPresetMapper.insert(preset);

        result.put("success", true);
        result.put("id", preset.getId());
        return result;
    }

    @Override
    public Map<String, Object> deletePresetPrompt(Long presetId) {
        ensureLessonPlanPromptPresetSchema();
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

        LessonPlanPromptPresetDto preset = lessonPlanPromptPresetMapper.selectById(presetId);
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

        lessonPlanPromptPresetMapper.deleteById(presetId);
        result.put("success", true);
        return result;
    }

    @Override
    public Map<String, Object> getConversationDetail(Integer conversationId) {
        ensureLessonPlanTaskSchema();
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
        if (hasQuestionHistory(teacher.getTeacherId(), conversationId)) {
            result.put("success", false);
            result.put("error", "该会话属于出题历史，请在教案模块选择教案历史会话");
            result.put("status", HttpStatus.BAD_REQUEST.value());
            return result;
        }

        List<LessonPlanTaskDto> tasks = lessonPlanTaskMapper.selectList(
                new QueryWrapper<LessonPlanTaskDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .eq("conversation_id", conversationId)
                        .orderByAsc("id")
        );

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        for (LessonPlanTaskDto task : tasks) {
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
                LessonPlanDto plan = lessonPlanMapper.selectOne(
                        new QueryWrapper<LessonPlanDto>().eq("task_id", task.getId()).last("LIMIT 1")
                );
                aiMap.put("content", plan == null ? "教案已生成，但未找到展示内容" : plan.getMarkdownContent());
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
    public Map<String, Object> createLessonPlanTask(String subject, String grade, String teachingTopic, String textbookVersion, Integer durationMinutes,
                                                    Integer interactionCount, String customRequirement, Integer conversationId,
                                                    Boolean useContext, Integer contextRounds) {
        ensureLessonPlanTaskSchema();
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
            conversation.setTitle("教案会话");
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
            if (hasQuestionHistory(teacher.getTeacherId(), actualConversationId)) {
                result.put("success", false);
                result.put("error", "该会话属于出题历史，请在教案模块选择教案历史会话");
                result.put("status", HttpStatus.BAD_REQUEST.value());
                return result;
            }
            existingConversation = conversation;
        }

        String normalizedSubject = normalizeText(subject);
        String normalizedGrade = normalizeText(grade);
        String normalizedTeachingTopic = normalizeText(teachingTopic);
        String normalizedTextbookVersion = normalizeText(textbookVersion);
        String normalizedRequirement = normalizeText(customRequirement);
        int normalizedDuration = durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
        int normalizedInteractionCount = interactionCount == null ? DEFAULT_INTERACTION_COUNT : interactionCount;

        if (enableContext) {
            LessonPlanTaskDto latestTask = findLatestTask(teacher.getTeacherId(), actualConversationId);
            if (latestTask != null) {
                if (isBlank(normalizedSubject)) normalizedSubject = latestTask.getSubject();
                if (isBlank(normalizedGrade)) normalizedGrade = latestTask.getGrade();
                if (isBlank(normalizedTeachingTopic)) normalizedTeachingTopic = latestTask.getTeachingTopic();
                if (durationMinutes == null && latestTask.getDurationMinutes() != null) {
                    normalizedDuration = latestTask.getDurationMinutes();
                }
                if (interactionCount == null && latestTask.getInteractionCount() != null) {
                    normalizedInteractionCount = latestTask.getInteractionCount();
                }
            }
        }

        String validationError = validateRequest(normalizedSubject, normalizedGrade, normalizedTeachingTopic, normalizedDuration, normalizedInteractionCount);
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

        String title = buildTitleFromForm(normalizedSubject, normalizedGrade, normalizedTeachingTopic, normalizedRequirement);
        if (isNewConversation || shouldRefreshConversationTitle(existingConversation)) {
            ConversationDto toUpdate = new ConversationDto();
            toUpdate.setId(actualConversationId);
            toUpdate.setTitle(title);
            conversationMapper.updateById(toUpdate);
        }

        String prompt = buildPrompt(normalizedSubject, normalizedGrade, normalizedTeachingTopic, normalizedTextbookVersion,
                normalizedDuration, normalizedInteractionCount, normalizedRequirement,
                enableContext, actualContextRounds, recentContextSummary);

        LessonPlanTaskDto task = new LessonPlanTaskDto();
        task.setTeacherId(teacher.getTeacherId());
        task.setConversationId(actualConversationId);
        task.setStatus("PENDING");
        task.setSubject(normalizedSubject);
        task.setGrade(normalizedGrade);
        task.setTeachingTopic(normalizedTeachingTopic);
        task.setDurationMinutes(normalizedDuration);
        task.setInteractionCount(normalizedInteractionCount);
        task.setContextUsed(enableContext);
        task.setContextRounds(actualContextRounds);
        task.setCustomRequirement(normalizedRequirement);
        task.setRequestPrompt(prompt);
        lessonPlanTaskMapper.insert(task);

        lessonPlanExecutor.execute(() -> executeTask(task.getId()));

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
    public Map<String, Object> getLessonPlanTaskStatus(Long taskId) {
        ensureLessonPlanTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }
        LessonPlanTaskDto task = lessonPlanTaskMapper.selectById(taskId);
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

        LessonPlanDto lessonPlan = lessonPlanMapper.selectOne(
                new QueryWrapper<LessonPlanDto>().eq("task_id", task.getId()).last("LIMIT 1")
        );
        if (lessonPlan != null) {
            result.put("planId", lessonPlan.getId());
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
    public Map<String, Object> listLessonPlans(Integer page, Integer pageSize) {
        ensureLessonPlanTaskSchema();
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
        long total = lessonPlanMapper.selectCount(
                new QueryWrapper<LessonPlanDto>().eq("teacher_id", teacher.getTeacherId())
        );
        int offset = (pageNo - 1) * size;
        List<LessonPlanDto> records = lessonPlanMapper.selectList(
                new QueryWrapper<LessonPlanDto>()
                        .eq("teacher_id", teacher.getTeacherId())
                        .orderByDesc("id")
                        .last("LIMIT " + offset + "," + size)
        );

        List<Map<String, Object>> plans = records.stream().map(plan -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", plan.getId());
            item.put("taskId", plan.getTaskId());
            item.put("title", plan.getTitle());
            item.put("subject", plan.getSubject());
            item.put("grade", plan.getGrade());
            item.put("teachingTopic", plan.getTeachingTopic());
            item.put("durationMinutes", plan.getDurationMinutes());
            item.put("interactionCount", plan.getInteractionCount());
            item.put("createdAt", plan.getCreatedAt());
            return item;
        }).toList();

        result.put("success", true);
        result.put("items", plans);
        result.put("page", pageNo);
        result.put("pageSize", size);
        result.put("total", total);
        return result;
    }

    @Override
    public Map<String, Object> getLessonPlanDetail(Long planId) {
        ensureLessonPlanTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        LessonPlanDto lessonPlan = lessonPlanMapper.selectById(planId);
        if (lessonPlan == null) {
            result.put("success", false);
            result.put("error", "教案不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!lessonPlan.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限访问该教案");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        result.put("success", true);
        result.put("id", lessonPlan.getId());
        result.put("taskId", lessonPlan.getTaskId());
        result.put("title", lessonPlan.getTitle());
        result.put("subject", lessonPlan.getSubject());
        result.put("grade", lessonPlan.getGrade());
        result.put("teachingTopic", lessonPlan.getTeachingTopic());
        result.put("durationMinutes", lessonPlan.getDurationMinutes());
        result.put("interactionCount", lessonPlan.getInteractionCount());
        result.put("overview", lessonPlan.getOverview());
        result.put("objectives", parseJsonArrayField(lessonPlan.getObjectivesJson()));
        result.put("keyPoints", parseJsonArrayField(lessonPlan.getKeyPointsJson()));
        result.put("difficultyPoints", parseJsonArrayField(lessonPlan.getDifficultyPointsJson()));
        result.put("teachingProcess", parseJsonNodeField(lessonPlan.getTeachingProcessJson()));
        result.put("homework", lessonPlan.getHomework());
        result.put("assessment", lessonPlan.getAssessment());
        result.put("extensions", lessonPlan.getExtensions());
        result.put("markdownContent", lessonPlan.getMarkdownContent());
        result.put("createdAt", lessonPlan.getCreatedAt());
        result.put("updatedAt", lessonPlan.getUpdatedAt());
        return result;
    }

    @Override
    public Map<String, Object> deleteLessonPlan(Long planId) {
        ensureLessonPlanTaskSchema();
        Map<String, Object> result = new HashMap<>();
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            result.put("success", false);
            result.put("error", "未登录");
            result.put("status", HttpStatus.UNAUTHORIZED.value());
            return result;
        }

        LessonPlanDto lessonPlan = lessonPlanMapper.selectById(planId);
        if (lessonPlan == null) {
            result.put("success", false);
            result.put("error", "教案不存在");
            result.put("status", HttpStatus.NOT_FOUND.value());
            return result;
        }
        if (!lessonPlan.getTeacherId().equals(teacher.getTeacherId())) {
            result.put("success", false);
            result.put("error", "无权限删除该教案");
            result.put("status", HttpStatus.FORBIDDEN.value());
            return result;
        }

        lessonPlanMapper.deleteById(planId);
        result.put("success", true);
        return result;
    }

    private void executeTask(Long taskId) {
        ensureLessonPlanTaskSchema();
        LessonPlanTaskDto task = lessonPlanTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        try {
            task.setStatus("RUNNING");
            task.setUpdatedAt(LocalDateTime.now());
            lessonPlanTaskMapper.updateById(task);

            DeepSeekChatClient.ChatResult chatResult = deepSeekChatClient.chat(task.getRequestPrompt());
            String generatedContent = chatResult.content();
            ParsedLessonPlan parsed = parseAndValidate(generatedContent, task.getInteractionCount());

            LessonPlanDto lessonPlan = new LessonPlanDto();
            lessonPlan.setTaskId(task.getId());
            lessonPlan.setTeacherId(task.getTeacherId());
            lessonPlan.setSubject(task.getSubject());
            lessonPlan.setGrade(task.getGrade());
            lessonPlan.setTeachingTopic(task.getTeachingTopic());
            lessonPlan.setDurationMinutes(task.getDurationMinutes());
            lessonPlan.setInteractionCount(task.getInteractionCount());
            lessonPlan.setTitle(parsed.title());
            lessonPlan.setOverview(parsed.overview());
            lessonPlan.setObjectivesJson(parsed.objectivesJson());
            lessonPlan.setKeyPointsJson(parsed.keyPointsJson());
            lessonPlan.setDifficultyPointsJson(parsed.difficultyPointsJson());
            lessonPlan.setTeachingProcessJson(parsed.teachingProcessJson());
            lessonPlan.setHomework(parsed.homework());
            lessonPlan.setAssessment(parsed.assessment());
            lessonPlan.setExtensions(parsed.extensions());
            lessonPlan.setMarkdownContent(buildMarkdown(task, parsed));
            lessonPlanMapper.insert(lessonPlan);

            task.setStatus("SUCCESS");
            task.setRawResponse(chatResult.rawResponse());
            task.setResultJson(parsed.resultJson());
            task.setErrorMessage(null);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            lessonPlanTaskMapper.updateById(task);
        } catch (Exception ex) {
            logger.error("教案任务执行失败, taskId={}", taskId, ex);
            task.setStatus("FAILED");
            task.setErrorMessage("生成失败: " + ex.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            lessonPlanTaskMapper.updateById(task);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean shouldRefreshConversationTitle(ConversationDto conversation) {
        if (conversation == null) {
            return false;
        }
        String title = conversation.getTitle();
        return title == null
                || title.isBlank()
                || "请发送消息".equals(title.trim())
                || "教案会话".equals(title.trim());
    }

    private LessonPlanTaskDto findLatestTask(Integer teacherId, Integer conversationId) {
        return lessonPlanTaskMapper.selectOne(
                new QueryWrapper<LessonPlanTaskDto>()
                        .eq("teacher_id", teacherId)
                        .eq("conversation_id", conversationId)
                        .orderByDesc("id")
                        .last("LIMIT 1")
        );
    }

    private boolean hasQuestionHistory(Integer teacherId, Integer conversationId) {
        Long generationCount = generationTaskMapper.selectCount(
                new QueryWrapper<GenerationTaskDto>()
                        .eq("teacher_id", teacherId)
                        .eq("conversation_id", conversationId)
        );
        return generationCount != null && generationCount > 0;
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
        List<LessonPlanTaskDto> taskRounds = lessonPlanTaskMapper.selectList(
                new QueryWrapper<LessonPlanTaskDto>()
                        .eq("conversation_id", conversationId)
                        .eq("status", "SUCCESS")
                        .orderByDesc("id")
                        .last("LIMIT " + rounds)
        );
        if (taskRounds == null || taskRounds.isEmpty()) {
            return "";
        }

        List<LessonPlanTaskDto> recentRounds = new java.util.ArrayList<>(taskRounds);
        java.util.Collections.reverse(recentRounds);
        LessonPlanTaskDto latest = recentRounds.get(recentRounds.size() - 1);
        StringBuilder summary = new StringBuilder();
        summary.append("【最近一轮教案基准】\n");
        summary.append("- 用户请求：").append(abbreviate(buildTaskBrief(latest), 180)).append("\n");
        summary.append("- 教案摘要：").append(abbreviate(extractTaskSummary(latest), 1200)).append("\n\n");
        summary.append("【历史轮次摘要】\n");
        for (int i = 0; i < recentRounds.size(); i++) {
            LessonPlanTaskDto task = recentRounds.get(i);
            summary.append("Round ").append(i + 1).append(":\n");
            summary.append("- 用户意图摘要：").append(abbreviate(buildTaskBrief(task), 160)).append("\n");
            summary.append("- AI产出摘要：").append(abbreviate(extractTaskSummary(task), 420)).append("\n");
        }
        return summary.toString().trim();
    }

    private String buildTaskBrief(LessonPlanTaskDto task) {
        StringBuilder brief = new StringBuilder();
        brief.append(task.getSubject()).append(" ").append(task.getGrade()).append(" ").append(task.getTeachingTopic());
        if (task.getCustomRequirement() != null && !task.getCustomRequirement().isBlank()) {
            brief.append(" - ").append(task.getCustomRequirement());
        }
        return brief.toString();
    }

    private String extractTaskSummary(LessonPlanTaskDto task) {
        if (task.getResultJson() == null || task.getResultJson().isBlank()) {
            return buildTaskBrief(task);
        }
        try {
            JsonNode root = objectMapper.readTree(task.getResultJson());
            StringBuilder summary = new StringBuilder();
            summary.append("标题：").append(root.path("title").asText("")).append("；");
            summary.append("概述：").append(abbreviate(root.path("overview").asText(""), 120));
            JsonNode process = root.path("teachingProcess");
            if (process.isArray()) {
                summary.append("；流程环节数：").append(process.size());
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

    private String buildTitleFromForm(String subject, String grade, String teachingTopic, String customRequirement) {
        StringBuilder title = new StringBuilder();
        title.append(subject).append(" ").append(grade).append(" ").append(teachingTopic);
        if (customRequirement != null && !customRequirement.isBlank()) {
            String customPart = customRequirement.length() > 20
                    ? customRequirement.substring(0, 20) + "..."
                    : customRequirement;
            title.append(" - ").append(customPart);
        }
        return title.toString();
    }

    private ParsedLessonPlan parseAndValidate(String content, Integer requiredInteractionCount) throws Exception {
        String normalized = normalizeModelContent(content);
        JsonNode root = objectMapper.readTree(normalized);

        String title = requiredText(root, "title");
        String overview = requiredText(root, "overview");
        JsonNode objectives = requiredArray(root, "objectives");
        JsonNode keyPoints = requiredArray(root, "keyPoints");
        JsonNode difficultyPoints = requiredArray(root, "difficultyPoints");
        JsonNode teachingProcess = requiredArray(root, "teachingProcess");
        String homework = requiredText(root, "homework");
        String assessment = requiredText(root, "assessment");
        String extensions = requiredText(root, "extensions");

        int interactionSteps = 0;
        for (JsonNode step : teachingProcess) {
            if (step.path("interactionDesign").isTextual() && !step.path("interactionDesign").asText().isBlank()) {
                interactionSteps++;
            }
        }
        if (interactionSteps < requiredInteractionCount) {
            throw new RuntimeException("互动环节数量不足，至少需要 " + requiredInteractionCount + " 个");
        }

        return new ParsedLessonPlan(
                title,
                overview,
                objectMapper.writeValueAsString(objectives),
                objectMapper.writeValueAsString(keyPoints),
                objectMapper.writeValueAsString(difficultyPoints),
                objectMapper.writeValueAsString(teachingProcess),
                homework,
                assessment,
                extensions,
                objectMapper.writeValueAsString(root)
        );
    }

    private String buildMarkdown(LessonPlanTaskDto task, ParsedLessonPlan parsed) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(parsed.title()).append("\n\n");
        builder.append("- 科目：").append(task.getSubject()).append("\n");
        builder.append("- 年级：").append(task.getGrade()).append("\n");
        builder.append("- 课题：").append(task.getTeachingTopic()).append("\n");
        builder.append("- 课时：").append(task.getDurationMinutes()).append(" 分钟\n");
        builder.append("- 互动环节：至少 ").append(task.getInteractionCount()).append(" 个\n\n");

        builder.append("## 教学概述\n").append(parsed.overview()).append("\n\n");
        builder.append("## 教学目标\n");
        appendArraySection(builder, parsed.objectivesJson());
        builder.append("\n## 重点\n");
        appendArraySection(builder, parsed.keyPointsJson());
        builder.append("\n## 难点\n");
        appendArraySection(builder, parsed.difficultyPointsJson());
        builder.append("\n## 教学过程\n");
        appendTeachingProcess(builder, parsed.teachingProcessJson());
        builder.append("\n## 作业设计\n").append(parsed.homework()).append("\n\n");
        builder.append("## 评价方式\n").append(parsed.assessment()).append("\n\n");
        builder.append("## 拓展建议\n").append(parsed.extensions()).append("\n");
        return builder.toString();
    }

    private void appendArraySection(StringBuilder builder, String json) throws Exception {
        JsonNode array = objectMapper.readTree(json);
        if (!array.isArray() || array.isEmpty()) {
            builder.append("- 无\n");
            return;
        }
        for (JsonNode item : array) {
            builder.append("- ").append(item.asText("")).append("\n");
        }
    }

    private void appendTeachingProcess(StringBuilder builder, String json) throws Exception {
        JsonNode array = objectMapper.readTree(json);
        if (!array.isArray() || array.isEmpty()) {
            builder.append("暂无教学过程\n");
            return;
        }
        int index = 1;
        for (JsonNode item : array) {
            builder.append(index++).append(". 【").append(item.path("stage").asText("未命名环节")).append("】 ")
                    .append(item.path("durationMinutes").asInt(0)).append(" 分钟\n")
                    .append("   - 教师活动：").append(item.path("teacherActivity").asText("")).append("\n")
                    .append("   - 学生活动：").append(item.path("studentActivity").asText("")).append("\n")
                    .append("   - 互动设计：").append(item.path("interactionDesign").asText("")).append("\n")
                    .append("   - 预期产出：").append(item.path("expectedOutcome").asText("")).append("\n");
        }
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new RuntimeException("缺少字段: " + field);
        }
        return node.asText().trim();
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

    private List<?> parseJsonArrayField(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object parseJsonNodeField(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildPrompt(String subject, String grade, String teachingTopic, String textbookVersion, int durationMinutes,
                               int interactionCount, String customRequirement, boolean useContext,
                               int contextRounds, String recentContextSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【角色设定】\n")
                .append("你是一位拥有20年教龄的").append(subject).append("特级教师，曾多次获得省级教学竞赛一等奖。")
                .append("你擅长设计“以学生为中心”的精品课教案，特别注重逻辑闭环、高阶思维引导和学科核心素养的落地。\n\n")
                .append("【任务目标】\n")
                .append("请为我生成一份完整的、可直接用于公开课或教学比赛的“").append(teachingTopic).append("”详细教案，教案中尽量不要使用过多分点，如有必要将分点改为1.2.3.数字分点。\n\n")
                .append("【硬性要求】\n")
                .append("这份教案必须具备以下特征，缺一不可：\n")
                .append("1. 完整性：必须包含教材分析、学情分析、教学目标（三维或核心素养）、重难点、教学准备、详细教学过程（含教师活动、学生活动、设计意图）、板书设计、作业设计、教学反思等全部模块。\n")
                .append("2. 逻辑闭环：从导入到小结，要有一条清晰的问题链或情境线贯穿始终，结尾必须回扣开头，形成“总-分-总”的认知闭环。\n")
                .append("3. 可视化：在教学过程中，需要详细写出教师关键引导语和过渡语，并预设学生3种可能回答及教师对应理答（答对如何追问深化、答错如何纠偏）。\n")
                .append("4. 分层设计：在练习和作业环节，必须体现基础巩固、拓展提升两个层次，并注明对应哪类学情学生。\n\n")
                .append("【教案结构框架】\n")
                .append("请严格按照以下结构填充内容，越详细越好：\n")
                .append("一、教材分析：教材版本、单元位置、本课在知识体系中的地位（承前启后作用）。\n")
                .append("二、学情分析：已有知识储备、可能学习困难（认知痛点）、针对痛点的破解策略。\n")
                .append("三、教学目标：按知识技能/过程方法/情感态度价值观或核心素养维度书写，使用可测动词（如能复述、能辨析、会运用）。\n")
                .append("四、教学重难点：重点为核心知识点；难点为最易混淆处并说明原因。\n")
                .append("五、教学准备：教师准备（课件、器材、模型等）与学生准备（预习、学具等）。\n")
                .append("六、教学过程（核心）：每个环节写明教师活动、学生活动、设计意图。\n")
                .append("  - 环节一：情境导入（X分钟），通过具体例子/实验创设认知冲突，提出具体问题。\n")
                .append("  - 环节二：新知探究（X分钟），分步骤推进，设置合作探究/实验/讨论，包含关键追问与预设理答。\n")
                .append("  - 环节三：巩固应用（X分钟），含分层练习：基础题（全员）与拓展题（学优生）。\n")
                .append("  - 环节四：小结升华（X分钟），引导自主总结并回扣导入问题，形成闭环。\n")
                .append("七、板书设计：用文字或符号图示呈现结构与知识关联。\n")
                .append("八、作业设计：必做作业（巩固类）与选做作业（实践/探究类）。\n")
                .append("九、教学反思（预写）：设计亮点、需留意的生成点及预案。\n\n")
                .append("【基本信息】\n")
                .append("- 科目：").append(subject).append("\n")
                .append("- 年级：").append(grade).append("\n")
                .append("- 课题：").append(teachingTopic).append("\n")
                .append("- 课时：1课时（").append(durationMinutes).append("分钟）\n")
                .append("- 教材版本：").append(isBlank(textbookVersion) ? "未指定（请结合该学段常用版本合理生成）" : textbookVersion).append("\n");
        if (customRequirement != null && !customRequirement.isBlank()) {
            prompt.append("- 教师补充要求：").append(customRequirement).append("\n");
        } else {
            prompt.append("- 教师补充要求：无（请给出通用高质量方案）\n");
        }
        prompt.append("\n【历史上下文】\n");
        if (!useContext) {
            prompt.append("本轮未启用历史上下文，请按本轮参数独立生成。\n");
        } else {
            prompt.append("本轮已启用历史上下文，最多关联最近").append(contextRounds).append("轮。\n");
            prompt.append(recentContextSummary).append("\n");
        }
        prompt.append("\n【生成要求】\n")
                .append("1) 教学过程必须细化到可直接上课执行，体现教师关键引导语、过渡语和理答策略。\n")
                .append("2) 练习与作业必须分层设计，明确对应学生层次。\n")
                .append("3) 全文保持学科严谨性、可操作性、公开课展示性。\n")
                .append("4) 时长分配合理，teachingProcess 的 durationMinutes 总和应接近总课时，并至少包含 ")
                .append(interactionCount).append(" 个有效互动环节。\n\n")
                .append("【输出格式约束】\n")
                .append("请严格只输出JSON，不要包含任何额外解释，不要使用Markdown代码块。\n")
                .append("字段名必须完全一致，JSON结构如下：\n")
                .append("{\n")
                .append("  \"title\": \"教案标题\",\n")
                .append("  \"overview\": \"本课概述\",\n")
                .append("  \"objectives\": [\"知识与技能：...\", \"过程与方法：...\", \"情感态度与价值观：...\"],\n")
                .append("  \"keyPoints\": [\"重点1\", \"重点2\"],\n")
                .append("  \"difficultyPoints\": [\"难点1\", \"难点2\"],\n")
                .append("  \"teachingProcess\": [\n")
                .append("    {\n")
                .append("      \"stage\": \"导入\",\n")
                .append("      \"durationMinutes\": 5,\n")
                .append("      \"teacherActivity\": \"教师做什么（含方法策略）\",\n")
                .append("      \"studentActivity\": \"学生做什么\",\n")
                .append("      \"interactionDesign\": \"互动方式（含分组建议与时长）\",\n")
                .append("      \"expectedOutcome\": \"预期产出（可观察/可评价）\"\n")
                .append("    }\n")
                .append("  ],\n")
                .append("  \"homework\": \"分层作业（基础/提升/挑战三级）\",\n")
                .append("  \"assessment\": \"课堂即时检测题+开放题+常见误解预警与纠正策略\",\n")
                .append("  \"extensions\": \"差异化支持（学困生/资优生/特殊需求）+资源整合建议+生活联系建议\"\n")
                .append("}\n\n")
                .append("【硬性校验】\n")
                .append("1) teachingProcess 至少输出 ").append(interactionCount).append(" 个包含 interactionDesign 的环节。\n")
                .append("2) 所有内容中文输出，表达具体、可落地。\n")
                .append("3) durationMinutes 累计应接近 ").append(durationMinutes).append(" 分钟。\n");
        return prompt.toString();
    }

    private String validateRequest(String subject, String grade, String teachingTopic, int durationMinutes, int interactionCount) {
        if (subject == null || subject.isBlank()) {
            return "科目不能为空";
        }
        if (grade == null || grade.isBlank()) {
            return "年级不能为空";
        }
        if (teachingTopic == null || teachingTopic.isBlank()) {
            return "课题不能为空";
        }
        if (durationMinutes < 20 || durationMinutes > 180) {
            return "课时长度需在20-180分钟之间";
        }
        if (interactionCount < 3 || interactionCount > 12) {
            return "互动环节数量需在3-12之间";
        }
        return null;
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureLessonPlanPromptPresetSchema() {
        if (lessonPlanPromptPresetSchemaChecked.get()) {
            return;
        }
        synchronized (lessonPlanPromptPresetSchemaChecked) {
            if (lessonPlanPromptPresetSchemaChecked.get()) {
                return;
            }
            try {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS lesson_plan_prompt_presets (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            teacher_id INT NULL COMMENT '创建教师ID，系统默认预设为空',
                            title VARCHAR(100) NOT NULL COMMENT '预设名称',
                            prompt_content TEXT NOT NULL COMMENT '预设Prompt内容',
                            is_system_default TINYINT(1) DEFAULT 0 NOT NULL COMMENT '是否系统默认预设',
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                            KEY idx_lpp_teacher (teacher_id),
                            KEY idx_lpp_system_default (is_system_default),
                            CONSTRAINT fk_lpp_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (teacher_id) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                seedSystemPresetPrompt("课堂节奏控制版", "加强课堂节奏控制：导入不超过5分钟，核心讲授分段推进，每10分钟加入一次互动检查点，结尾留3分钟课堂小结。");
                seedSystemPresetPrompt("分层教学加强版", "请设计分层教学方案：同一环节需提供基础任务、进阶任务、挑战任务，并写出针对学困生与资优生的具体指导语。");
                seedSystemPresetPrompt("探究互动优先版", "请将教学过程设计为探究驱动：至少包含3次小组协作或同伴讨论，明确每次互动的目标、流程、教师追问与预期产出。");
                seedSystemPresetPrompt("考试导向巩固版", "请强化考试能力训练：突出高频考点、易错点和答题规范，每个关键环节加入1个即时检测问题并附纠错建议。");
                lessonPlanPromptPresetSchemaChecked.set(true);
            } catch (Exception ex) {
                logger.error("自动补齐 lesson_plan_prompt_presets 表结构失败", ex);
                throw new RuntimeException("lesson_plan_prompt_presets 表结构缺失，且自动迁移失败，请手动执行 aiTeacher.sql 中相关建表语句");
            }
        }
    }

    private void seedSystemPresetPrompt(String title, String content) {
        jdbcTemplate.update(
                """
                        INSERT INTO lesson_plan_prompt_presets (teacher_id, title, prompt_content, is_system_default)
                        SELECT NULL, ?, ?, 1
                        FROM DUAL
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM lesson_plan_prompt_presets
                            WHERE is_system_default = 1
                              AND title = ?
                        )
                        """,
                title, content, title
        );
    }

    private void ensureLessonPlanTaskSchema() {
        if (lessonPlanTaskSchemaChecked.get()) {
            return;
        }
        synchronized (lessonPlanTaskSchemaChecked) {
            if (lessonPlanTaskSchemaChecked.get()) {
                return;
            }
            try {
                ensureColumn("conversation_id", "ALTER TABLE lesson_plan_tasks ADD COLUMN conversation_id INT NULL COMMENT '会话ID'");
                ensureColumn("context_used", "ALTER TABLE lesson_plan_tasks ADD COLUMN context_used TINYINT(1) DEFAULT 0 NOT NULL COMMENT '是否启用上下文'");
                ensureColumn("context_rounds", "ALTER TABLE lesson_plan_tasks ADD COLUMN context_rounds INT DEFAULT 5 NOT NULL COMMENT '关联上下文轮次'");
                lessonPlanTaskSchemaChecked.set(true);
            } catch (Exception ex) {
                logger.error("自动补齐 lesson_plan_tasks 表结构失败", ex);
                throw new RuntimeException("lesson_plan_tasks 表结构缺失，且自动迁移失败，请手动执行 aiTeacher.sql 中 lesson_plan_tasks 的 ALTER 语句");
            }
        }
    }

    private void ensureColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson_plan_tasks' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(alterSql);
        logger.info("已自动补齐 lesson_plan_tasks.{} 字段", columnName);
    }

    private record ParsedLessonPlan(
            String title,
            String overview,
            String objectivesJson,
            String keyPointsJson,
            String difficultyPointsJson,
            String teachingProcessJson,
            String homework,
            String assessment,
            String extensions,
            String resultJson
    ) {
    }
}
