// java
package com.leo.aiteacher.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.pojo.dto.ConversationDto;
import com.leo.aiteacher.pojo.dto.MessageDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.ConversationMapper;
import com.leo.aiteacher.pojo.mapper.MessageMapper;
import com.leo.aiteacher.service.TeachingPlanService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.leo.aiteacher.util.SessionUtils;

@Service
public class TeachingPlanServiceImpl implements TeachingPlanService {

    private static final Logger logger = LoggerFactory.getLogger(TeachingPlanServiceImpl.class);

    @Value("${deepseek.api.url}")
    private String DEEPSEEK_API_URL;

    @Value("${deepseek.api.key}")
    private String API_KEY;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConversationMapper conversationMapper;

    /**
     * 生成教学计划
     * @param userMessage 用户输入的消息
     * @param conversationId 当前会话的ID
     * @return 包含生成的教学计划信息的Map对象
     */
    @Override
    public Map<String, Object> generateTeachingQuestion(String userMessage, Integer conversationId) {
        logger.info("Received userMessage: {}", userMessage);
        // 提取关键字段作为标题
        String title = extractKeyFields(userMessage);
        logger.info("title: {}", title);

        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                Map<String, Object> unauth = new HashMap<>();
                unauth.put("success", false);
                unauth.put("error", "未登录");
                unauth.put("status", HttpStatus.UNAUTHORIZED.value());
                return unauth;
            }

            // 如果 conversationId 为 null，自动创建新对话
            Integer actualConversationId = conversationId;
            boolean isNewConversation = false;
            logger.info("当前会话id: {}", actualConversationId);
            if (conversationId == null) {
                // 自动创建新对话
                ConversationDto newConversation = new ConversationDto();
                newConversation.setTeacherId(teacher.getTeacherId());
                newConversation.setTitle(title);
                conversationMapper.insertConversation(newConversation);
                actualConversationId = newConversation.getId();
                isNewConversation = true;
                logger.info("自动创建新对话，ID: {}", actualConversationId);
            }

            // 记录当前用户基本信息（脱敏或只记录必要字段）
            logger.info("当前请求用户: id={} name={}", teacher.getTeacherId(), teacher.getTeacherName());

            if (API_KEY == null || API_KEY.trim().isEmpty()) {
                logger.error("API Key is missing!");
                throw new RuntimeException("API密钥未配置");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String authHeader = "Bearer " + API_KEY;
            headers.set("Authorization", authHeader);

            // 脱敏处理后记录日志
            String maskedAuthHeader = authHeader.replaceAll("(?<=.{6}).", "*");
            logger.info("Auth Header: {}", maskedAuthHeader);

            if (userMessage.trim().isEmpty()) {
                logger.warn("消息内容不能为空");
                throw new RuntimeException("消息内容不能为空");
            }

            // 可以将当前用户信息附加到消息中，便于 AI 返回个性化内容（可选）
            String enrichedMessage = String.format("教师[%s|%s]：%s",
                    teacher.getTeacherId() == null ? "" : teacher.getTeacherId(),
                    teacher.getTeacherName() == null ? "" : teacher.getTeacherName(),
                    userMessage);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", enrichedMessage
            )));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();
            logger.info("Calling DeepSeek API: {}", DEEPSEEK_API_URL);

            ResponseEntity<String> response = restTemplate.exchange(
                    DEEPSEEK_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            logger.info("API Response Status: {}", response.getStatusCode());
            logger.info("API Response Body: {}", response.getBody());

            // 关键修改：使用 asText() 获取未转义的纯文本，保留公式标记（比如 $$...$$）
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
            String content = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");

            // 聊天插入数据库，保存原始文本（含 Markdown/LaTeX 标记）
            MessageDto messageDto = new MessageDto();
            messageDto.setConversationId(actualConversationId);
            messageDto.setQuestion(title);
            messageDto.setAnswer(content);
            messageMapper.insertMessage(messageDto);

            //修改title
            ConversationDto conversation = conversationMapper.getConversationById(actualConversationId);
            if (conversation != null && (conversation.getTitle() == null || conversation.getTitle().equals("请发送消息"))) {
                conversation.setTitle(title);
                conversationMapper.updateById(conversation);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("reply", content);

            // 返回当前登录用户的基本信息给前端
            result.put("teacherId", teacher.getTeacherId());
            result.put("teacherName", teacher.getTeacherName());
            // 如果是新创建的对话，返回对话ID
            if (isNewConversation) {
                result.put("newConversationId", actualConversationId);
            }

            return result;

        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error Status: {}", e.getStatusCode());
            logger.error("HTTP Error Response: {}", e.getResponseBodyAsString());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "调用AI失败");
            errorResponse.put("status", e.getStatusCode().value());
            errorResponse.put("message", e.getResponseBodyAsString());

            return errorResponse;
        } catch (Exception e) {
            logger.error("Internal Error: ", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "内部错误");
            errorResponse.put("message", e.getMessage());

            return errorResponse;
        }
    }

    // 提取关键字段的正则表达式方法
    String extractKeyFields(String userMessage) {
        // 定义固定前缀
        String fixedPrefix = "现在你是一位资深高级教师，要求直接给出题目，不要任何多余的任何信息，并且在最后给我答案和详细的解析，严禁做任何非学习相关的内容，如果有非学习相关的请求必须拒答,你是";

        // 移除固定前缀
        String cleanedMessage;
        if (userMessage.startsWith(fixedPrefix)) {
            cleanedMessage = userMessage.substring(fixedPrefix.length());
        } else {
            cleanedMessage = userMessage;
        }

        String patternWithTopic = "(.*?)老师，出(.*?)的(.*?)题目，共(.*?)，(.*)";
        String patternWithoutTopic = "(.*?)老师，出(.*?)的(.*?)题目，共(.*)";

        java.util.regex.Pattern regexWithTopic = java.util.regex.Pattern.compile(patternWithTopic);
        java.util.regex.Pattern regexWithoutTopic = java.util.regex.Pattern.compile(patternWithoutTopic);
        java.util.regex.Matcher matcherWithTopic = regexWithTopic.matcher(cleanedMessage);
        java.util.regex.Matcher matcherWithoutTopic = regexWithoutTopic.matcher(cleanedMessage);

        // 优先匹配带主题内容的模式
        if (matcherWithTopic.find()) {
            String subject = matcherWithTopic.group(1);      // 学科
            String difficulty = matcherWithTopic.group(2);   // 难度
            String questionType = matcherWithTopic.group(3); // 题型
            String count = matcherWithTopic.group(4);        // 数量
            String topic = matcherWithTopic.group(5);        // 主题内容

            return String.format("%s %s %s %s %s", subject, difficulty, questionType, count, topic);
        }
        // 如果没有找到带主题内容的匹配，则尝试不带主题内容的模式
        else if (matcherWithoutTopic.find()) {
            String subject = matcherWithoutTopic.group(1);      // 学科
            String difficulty = matcherWithoutTopic.group(2);   // 难度
            String questionType = matcherWithoutTopic.group(3); // 题型
            String count = matcherWithoutTopic.group(4);        // 数量（这里包含了数量和可能的其他内容）

            return String.format("%s %s %s %s", subject, difficulty, questionType, count);
        }
        // 如果不匹配，返回空字符串作为标题
        return "";
    }

    /**
     * 创建新对话
     * @return 包含创建结果和新对话ID的Map对象
     */
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
            // 检查是否已存在未使用的对话（标题为"请发送消息"的对话）
            List<ConversationDto> existingConversations = conversationMapper.getConversationsByTeacherId(teacher.getTeacherId());
            ConversationDto unusedConversation = existingConversations.stream()
                    .filter(conversation -> "请发送消息".equals(conversation.getTitle()))
                    .findFirst()
                    .orElse(null);

            // 如果存在未使用的对话，直接返回该对话ID
            if (unusedConversation != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("conversationId", unusedConversation.getId());
                return result;
            }

            // 创建新对话
            ConversationDto conversation = new ConversationDto();
            conversation.setTeacherId(teacher.getTeacherId());
            conversation.setTitle("请发送消息");

            logger.debug("准备插入对话记录: teacherId={}, title={}", conversation.getTeacherId(), conversation.getTitle());

            conversationMapper.insertConversation(conversation);

            logger.info("成功创建对话，对话ID: {}", conversation.getId());

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

    /**
     * 获取用户的对话列表
     * @return 包含对话列表的Map对象
     */
    @Override
    public List<Map<String, Object>> getUserConversations() {
        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                throw new RuntimeException("未登录");
            }

            logger.info("获取用户{}的对话列表", teacher.getTeacherId());

            // 查询该用户的所有对话
            List<ConversationDto> conversations = conversationMapper.getConversationsByTeacherIdWithLatestMessage(teacher.getTeacherId());

            // 转换为Map格式返回
            return conversations.stream().map(conversation -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", conversation.getId());
                // 使用消息表的updated_at作为createTime
                map.put("createTime", conversation.getLatestMessageUpdatedAt());
                map.put("title", conversation.getTitle());
                return map;
            }).collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            logger.error("获取用户对话列表失败: ", e);
            throw new RuntimeException("获取对话列表失败: " + e.getMessage());
        }
    }


    /**
     * 获取指定对话的详细消息列表
     * @param conversationId 对话的唯一标识符
     * @return 包含消息列表的Map对象
     */
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

            logger.info("获取对话详情: conversationId={}, userId={}", conversationId, teacher.getTeacherId());

            // 验证对话是否存在且属于当前用户
            ConversationDto conversation = conversationMapper.getConversationById(conversationId);
            if (conversation == null) {
                logger.warn("对话不存在: id={}", conversationId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "对话不存在");
                errorResponse.put("status", HttpStatus.NOT_FOUND.value());
                return errorResponse;
            }

            if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
                logger.warn("无权限访问对话: conversationId={}, userId={}", conversationId, teacher.getTeacherId());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "无权限访问该对话");
                errorResponse.put("status", HttpStatus.FORBIDDEN.value());
                return errorResponse;
            }

            // 获取对话中的所有消息
            List<MessageDto> messages = messageMapper.getMessagesByConversationId(conversationId);

            // 转换消息格式：将每条记录的 question 和 answer 分别作为独立消息条目加入列表
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

    /**
     * 删除指定对话及其所有消息
     * @param conversationId 对话的唯一标识符
     * @return 包含删除结果的Map对象
     */
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

            logger.info("删除对话: conversationId={}, userId={}", conversationId, teacher.getTeacherId());

            // 验证对话是否存在且属于当前用户
            ConversationDto conversation = conversationMapper.getConversationById(conversationId);
            if (conversation == null) {
                logger.warn("对话不存在: id={}", conversationId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "对话不存在");
                errorResponse.put("status", HttpStatus.NOT_FOUND.value());
                return errorResponse;
            }

            if (!conversation.getTeacherId().equals(teacher.getTeacherId())) {
                logger.warn("无权限删除对话: conversationId={}, userId={}", conversationId, teacher.getTeacherId());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "无权限删除该对话");
                errorResponse.put("status", HttpStatus.FORBIDDEN.value());
                return errorResponse;
            }

            // 删除对话
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
