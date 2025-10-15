// TeachingPlanService.java
package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface TeachingPlanService {
    Map<String, Object> generateTeachingQuestion(String message , Integer conversationId);

    Map<String, Object> createConversation();

    List<Map<String,Object>> getUserConversations();

    Map<String, Object> getConversationDetail(Integer conversationId);

    Map<String, Object> deleteConversation(Integer conversationId);
}
