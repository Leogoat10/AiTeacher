package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.pojo.dto.ConversationDto;
import com.leo.aiteacher.pojo.dto.MessageDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.ConversationMapper;
import com.leo.aiteacher.pojo.mapper.MessageMapper;
import com.leo.aiteacher.service.TeachingPlanQueService;
import com.leo.aiteacher.util.SessionUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeachingPlanQueServiceImpl implements TeachingPlanQueService {

    private static final Logger logger = LoggerFactory.getLogger(TeachingPlanQueServiceImpl.class);

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Override
    public Map<String, Object> createConversation() {
        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                Map<String, Object> unauth = new HashMap<>();
                unauth.put("success", false);
                unauth.put("error", "未登录");
                return unauth;
            }

            List<ConversationDto> existingConversations = conversationMapper.getConversationsByTeacherId(teacher.getTeacherId());
            ConversationDto unusedConversation = existingConversations.stream()
                    .filter(conversation -> "请发送消息".equals(conversation.getTitle()))
                    .findFirst()
                    .orElse(null);

            if (unusedConversation != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("conversationId", unusedConversation.getId());
                return result;
            }

            ConversationDto conversation = new ConversationDto();
            conversation.setTeacherId(teacher.getTeacherId());
            conversation.setTitle("请发送消息");
            conversationMapper.insertConversation(conversation);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("conversationId", conversation.getId());
            return result;
        } catch (Exception e) {
            logger.error("创建对话失败: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "创建对话失败");
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public List<Map<String, Object>> getUserConversations() {
        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                throw new RuntimeException("未登录");
            }

            List<ConversationDto> conversations = conversationMapper.getConversationsByTeacherIdWithLatestMessage(teacher.getTeacherId());

            return conversations.stream().map(conversation -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", conversation.getId());
                map.put("createTime", conversation.getLatestMessageUpdatedAt());
                map.put("title", conversation.getTitle());
                return map;
            }).toList();
        } catch (Exception e) {
            logger.error("获取用户对话列表失败: ", e);
            throw new RuntimeException("获取对话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getConversationDetail(Integer conversationId) {
        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                Map<String, Object> unauth = new HashMap<>();
                unauth.put("success", false);
                unauth.put("error", "未登录");
                unauth.put("status", HttpStatus.UNAUTHORIZED.value());
                return unauth;
            }

            ConversationDto conversation = conversationMapper.getConversationById(conversationId);
            if (conversation == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "对话不存在");
                errorResponse.put("status", HttpStatus.NOT_FOUND.value());
                return errorResponse;
            }

            if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "无权限访问该对话");
                errorResponse.put("status", HttpStatus.FORBIDDEN.value());
                return errorResponse;
            }

            List<MessageDto> messages = messageMapper.getMessagesByConversationId(conversationId);
            List<Map<String, Object>> messageList = new java.util.ArrayList<>();
            for (MessageDto message : messages) {
                if (message.getQuestion() != null && !message.getQuestion().isEmpty()) {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("role", "user");
                    userMap.put("content", message.getQuestion());
                    userMap.put("timestamp", message.getCreatedAt());
                    messageList.add(userMap);
                }
                if (message.getAnswer() != null && !message.getAnswer().isEmpty()) {
                    Map<String, Object> aiMap = new HashMap<>();
                    aiMap.put("role", "ai");
                    aiMap.put("content", message.getAnswer());
                    aiMap.put("timestamp", message.getCreatedAt());
                    messageList.add(aiMap);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("messages", messageList);
            return result;
        } catch (Exception e) {
            logger.error("获取对话详情失败: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "获取对话详情失败");
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public Map<String, Object> deleteConversation(Integer conversationId) {
        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                Map<String, Object> unauth = new HashMap<>();
                unauth.put("success", false);
                unauth.put("error", "未登录");
                unauth.put("status", HttpStatus.UNAUTHORIZED.value());
                return unauth;
            }

            ConversationDto conversation = conversationMapper.getConversationById(conversationId);
            if (conversation == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "对话不存在");
                errorResponse.put("status", HttpStatus.NOT_FOUND.value());
                return errorResponse;
            }

            if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "无权限删除该对话");
                errorResponse.put("status", HttpStatus.FORBIDDEN.value());
                return errorResponse;
            }

            conversationMapper.deleteConversationById(conversationId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return result;
        } catch (Exception e) {
            logger.error("删除对话失败: ", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "删除对话失败");
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }
}
