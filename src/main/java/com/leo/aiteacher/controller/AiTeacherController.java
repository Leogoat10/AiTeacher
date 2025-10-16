// AiTeacherController.java (修改后)
package com.leo.aiteacher.controller;

import com.leo.aiteacher.service.TeachingPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/teacher")
public class AiTeacherController {

    // 添加日志记录器
    private static final Logger logger = LoggerFactory.getLogger(AiTeacherController.class);

    @Autowired
    private TeachingPlanService teachingPlanService;

    /**
     * 处理教学问题生成请求（新版：接收表单数据）
     * @param requestData 包含表单数据：subject, difficulty, questionType, questionCount, customMessage, conversationId
     * @return 响应实体，包含生成的教学问题或错误信息
     */
    @PostMapping("/plan")
    public ResponseEntity<?> generateTeachingPlan(@RequestBody Map<String, Object> requestData) {
        logger.info("Received plan request data: {}", requestData);
        try {
            if (requestData == null || requestData.isEmpty()) {
                logger.warn("请求数据不能为空");
                return ResponseEntity.badRequest().body("请求数据不能为空");
            }

            Integer conversationId = (Integer) requestData.get("conversationId");
            String subject = (String) requestData.get("subject");
            String difficulty = (String) requestData.get("difficulty");
            String questionType = (String) requestData.get("questionType");
            String questionCount = (String) requestData.get("questionCount");
            String customMessage = (String) requestData.get("customMessage");

            Map<String, Object> result = teachingPlanService.generateTeachingPlan(
                subject, difficulty, questionType, questionCount, customMessage, conversationId
            );

            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                int status = result.containsKey("status") ? (int) result.get("status") : 500;
                return ResponseEntity.status(status).body(result);
            }

        } catch (Exception e) {
            logger.error("Internal Error: ", e);

            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 处理教学问题生成请求（旧版：接收完整消息）
     * @param requestData 包含用户消息和可选的 conversationId
     * @return 响应实体，包含生成的教学问题或错误信息
     */
    @PostMapping("/question")
    public ResponseEntity<?> createTeachingPlan(@RequestBody Map<String, Object> requestData) {
        logger.info("Received request data: {}", requestData);
        try {
            if (requestData == null || requestData.isEmpty()) {
                logger.warn("请求数据不能为空");
                return ResponseEntity.badRequest().body("请求数据不能为空");
            }

            // 尝试从当前请求上下文获取 HttpServletRequest（不改变方法签名）
            Integer currentConversationId = (Integer) requestData.get("conversationId");

            String userMessage = (String) requestData.get("message");
            Map<String, Object> result = teachingPlanService.generateTeachingQuestion(userMessage, currentConversationId);

            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                int status = result.containsKey("status") ? (int) result.get("status") : 500;
                return ResponseEntity.status(status).body(result);
            }

        } catch (Exception e) {
            logger.error("Internal Error: ", e);

            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 创建新的对话
     * @return 响应实体，包含新创建的对话信息或错误信息
     */

    @PostMapping("/newConversation")
    public ResponseEntity<?> createConversation() {
        try {
            Map<String, Object> result = teachingPlanService.createConversation();

            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            logger.error("创建对话接口异常: ", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 获取用户的问题列表
     * @return 响应实体，包含对话列表或错误信息
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations() {
        try {
            List<Map<String, Object>> conversations = teachingPlanService.getUserConversations();
            Map<String, Object> result = Map.of(
                "success", true,
                "conversations", conversations
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取用户对话列表异常: ", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


    /**
     * 获取指定对话的详细信息
     * @param conversationId 对话ID
     * @return 响应实体，包含对话详情或错误信息
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<?> getConversationDetail(@PathVariable Integer conversationId) {
        try {
            Map<String, Object> result = teachingPlanService.getConversationDetail(conversationId);

            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                int status = result.containsKey("status") ? (int) result.get("status") : 500;
                return ResponseEntity.status(status).body(result);
            }
        } catch (Exception e) {
            logger.error("获取对话详情异常: ", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 删除指定对话
     * @param requestData 包含 conversationId 的请求数据
     * @return 响应实体，包含删除结果或错误信息
     */

    @PostMapping("/deleteConversation")
    public ResponseEntity<?> deleteConversation(@RequestBody Map<String, Object> requestData) {
        try {
            if (requestData == null || !requestData.containsKey("conversationId")) {
                logger.warn("请求数据无效，缺少 conversationId");
                return ResponseEntity.badRequest().body("请求数据无效，缺少 conversationId");
            }

            Integer conversationId = (Integer) requestData.get("conversationId");
            Map<String, Object> result = teachingPlanService.deleteConversation(conversationId);

            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                int status = result.containsKey("status") ? (int) result.get("status") : 500;
                return ResponseEntity.status(status).body(result);
            }
        } catch (Exception e) {
            logger.error("删除对话异常: ", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "error", "内部错误",
                "message", e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }


}
