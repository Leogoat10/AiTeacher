package com.leo.aiteacher.service;

import java.util.Map;

public interface LessonPlanService {

    Map<String, Object> createConversation();

    Map<String, Object> getConversationDetail(Integer conversationId);

    Map<String, Object> getConversations();

    Map<String, Object> listPresetPrompts();

    Map<String, Object> createPresetPrompt(String title, String promptContent);

    Map<String, Object> deletePresetPrompt(Long presetId);

    Map<String, Object> createLessonPlanTask(String subject, String grade, String teachingTopic, String textbookVersion,
                                             Integer durationMinutes, Integer interactionCount,
                                             String customRequirement, Integer conversationId,
                                             Boolean useContext, Integer contextRounds);

    Map<String, Object> getLessonPlanTaskStatus(Long taskId);

    Map<String, Object> listLessonPlans(Integer page, Integer pageSize);

    Map<String, Object> getLessonPlanDetail(Long planId);

    Map<String, Object> deleteLessonPlan(Long planId);
}
