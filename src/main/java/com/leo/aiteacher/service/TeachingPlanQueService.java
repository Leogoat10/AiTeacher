// TeachingPlanService.java
package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface TeachingPlanQueService {
    
    /**
     * 生成教学问题（新版：接收表单数据）
     */
    Map<String, Object> generateTeachingQuestion(String subject,String grade, String difficulty, String questionType,
                                                 String questionCount, String customMessage, Integer conversationId);

    Map<String, Object> createConversation();

    List<Map<String,Object>> getUserConversations();

    Map<String, Object> getConversationDetail(Integer conversationId);

    Map<String, Object> deleteConversation(Integer conversationId);
}
