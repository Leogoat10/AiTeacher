// TeachingPlanService.java
package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface TeachingPlanQueService {

    Map<String, Object> createConversation();

    List<Map<String,Object>> getUserConversations();

    Map<String, Object> getConversationDetail(Integer conversationId);

    Map<String, Object> deleteConversation(Integer conversationId);
}
