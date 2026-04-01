package com.leo.aiteacher.service;

import java.util.Map;

public interface QuestionGenerationTaskService {

    Map<String, Object> createGenerationTask(String subject, String grade, String difficulty, String questionType,
                                             String questionCount, String customMessage, Integer conversationId);

    Map<String, Object> getGenerationTaskStatus(Long taskId);
}
