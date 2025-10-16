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
     * 从表单数据构造提示词
     */
    private String buildPromptFromForm(String subject, String difficulty, String questionType,
                                       String questionCount, String customMessage) {
        StringBuilder prompt = new StringBuilder();

        // 1. 明确角色定位
        prompt.append("你是一位专业的").append(subject).append("教师，需要为学生出题用于教学和考核。\n\n");

        // 2. 题目要求
        prompt.append("题目要求：\n");
        prompt.append("- 科目：").append(subject).append("\n");
        prompt.append("- 难度：").append(difficulty).append("\n");
        prompt.append("- 题型：").append(questionType).append("\n");
        
        if (questionCount != null && !questionCount.trim().isEmpty()) {
            prompt.append("- 数量：").append(questionCount).append("\n");
        }
        
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            prompt.append("- 特殊要求：").append(customMessage).append("\n");
        }
        
        prompt.append("\n");

        // 3. 输出格式要求
        prompt.append("请按照以下格式输出：\n");
        prompt.append("1. 直接给出题目内容，明确标注每个题目的题号\n");
        prompt.append("2. 题目描述要清晰、准确、完整\n");
        prompt.append("3. 在最后给出各题的标准答案\n");
        prompt.append("4. 提供详细的解析和解题思路\n");
        prompt.append("5. 如果是填空题，标明具体填空位置；如果是简答题有多个小问，明确标注题号\n");
        prompt.append("6. 每道题标明分数\n\n");

        // 4. 重要说明
        prompt.append("重要说明：\n");
        prompt.append("- 题目难度应符合").append(difficulty).append("水平\n");
        prompt.append("- 不要输出任何与题目无关的多余信息\n");
        prompt.append("- 严禁输出任何非学习相关的内容，如有非学习相关的请求必须拒答");
        
        return prompt.toString();
    }

    /**
     * 从表单数据构造标题
     */
    private String buildTitleFromForm(String subject, String difficulty, String questionType,
                                      String questionCount, String customMessage) {
        StringBuilder title = new StringBuilder();
        title.append(subject).append(" ").append(difficulty).append(" ").append(questionType);
        
        if (questionCount != null && !questionCount.trim().isEmpty()) {
            title.append(" ").append(questionCount).append("题");
        }
        
        if (customMessage != null && !customMessage.trim().isEmpty()) {
            // 截取自定义消息的前20个字符作为标题的一部分
            String customPart = customMessage.length() > 20 
                ? customMessage.substring(0, 20) + "..." 
                : customMessage;
            title.append(" - ").append(customPart);
        }
        
        return title.toString();
    }

    /**
     * 生成题目（新版：接收表单数据）
     * @param subject 科目/专业
     * @param difficulty 难易程度
     * @param questionType 题型
     * @param questionCount 题目数量
     * @param customMessage 自定义消息
     * @param conversationId 当前会话的ID
     * @return 包含生成的教学题目的Map对象
     */
    @Override
    public Map<String, Object> generateTeachingQuestion(String subject, String difficulty, String questionType,
                                                        String questionCount, String customMessage, Integer conversationId) {
        logger.info("Received form data: subject={}, difficulty={}, questionType={}, questionCount={}, customMessage={}",
                subject, difficulty, questionType, questionCount, customMessage);

        try {
            TeacherDto teacher = SessionUtils.getCurrentTeacher();
            if (teacher == null) {
                Map<String, Object> unauth = new HashMap<>();
                unauth.put("success", false);
                unauth.put("error", "未登录");
                unauth.put("status", HttpStatus.UNAUTHORIZED.value());
                return unauth;
            }

            // 从表单数据提取标题
            String title = buildTitleFromForm(subject, difficulty, questionType, questionCount, customMessage);
            logger.info("Generated title: {}", title);

            // 从表单数据构造提示词
            String promptMessage = buildPromptFromForm(subject, difficulty, questionType, questionCount, customMessage);
            logger.info("Generated prompt: {}", promptMessage);

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

            // 记录当前用户基本信息
            logger.info("当前请求用户: id={} name={}", teacher.getTeacherId(), teacher.getTeacherName());

            if (API_KEY == null || API_KEY.trim().isEmpty()) {
                logger.error("API Key is missing!");
                throw new RuntimeException("API密钥未配置");
            }

            // 调用AI API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + API_KEY);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", promptMessage
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

            // 解析AI响应
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
            String content = contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");

            // 保存到数据库
            MessageDto messageDto = new MessageDto();
            messageDto.setConversationId(actualConversationId);
            messageDto.setQuestion(title);
            messageDto.setAnswer(content);
            messageMapper.insertMessage(messageDto);

            // 更新会话标题
            ConversationDto conversation = conversationMapper.getConversationById(actualConversationId);
            if (conversation != null && (conversation.getTitle() == null || conversation.getTitle().equals("请发送消息"))) {
                conversation.setTitle(title);
                conversationMapper.updateById(conversation);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("reply", content);
            result.put("teacherId", teacher.getTeacherId());
            result.put("teacherName", teacher.getTeacherName());
            
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
