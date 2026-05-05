package com.leo.aiteacher.service;

import java.util.Map;

public interface ExamPaperService {

    Map<String, Object> listPresetPrompts();

    Map<String, Object> createPresetPrompt(String title, String promptContent);

    Map<String, Object> deletePresetPrompt(Long presetId);

    Map<String, Object> createExamPaperTask(String subject, String grade, String examType,
                                            Integer durationMinutes, Integer totalScore,
                                            Integer questionCount, Map<String, Integer> questionTypeCounts, String difficulty,
                                            String knowledgePoints, String customRequirement);

    Map<String, Object> getExamPaperTaskStatus(Long taskId);

    Map<String, Object> listExamPapers(Integer page, Integer pageSize);

    Map<String, Object> getExamPaperDetail(Long paperId);

    Map<String, Object> deleteExamPaper(Long paperId);
}
