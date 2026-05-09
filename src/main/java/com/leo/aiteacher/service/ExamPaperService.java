package com.leo.aiteacher.service;

import java.util.Map;

public interface ExamPaperService {

    Map<String, Object> createConversation();

    Map<String, Object> getConversationDetail(Integer conversationId);

    Map<String, Object> getConversations();

    Map<String, Object> deleteConversation(Integer conversationId);

    Map<String, Object> listPresetPrompts();

    Map<String, Object> createPresetPrompt(String title, String promptContent);

    Map<String, Object> deletePresetPrompt(Long presetId);

    Map<String, Object> createExamPaperTask(String subject, String grade, String examType, String textbookVersion,
                                            Integer durationMinutes, Integer totalScore,
                                            Integer questionCount, Map<String, Integer> questionTypeCounts, String difficulty,
                                            String knowledgePoints, String customRequirement, Integer conversationId,
                                            Boolean useContext, Integer contextRounds);

    Map<String, Object> getExamPaperTaskStatus(Long taskId);

    Map<String, Object> listExamPapers(Integer page, Integer pageSize);

    Map<String, Object> getExamPaperDetail(Long paperId);

    Map<String, Object> deleteExamPaper(Long paperId);
}
