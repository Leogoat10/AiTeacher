// AiTeacherController.java (修改后)
package com.leo.aiteacher.controller;

import com.leo.aiteacher.service.TeachingPlanQueService;
import com.leo.aiteacher.service.QuestionGenerationTaskService;
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
    private TeachingPlanQueService teachingPlanQueService;

    @Autowired
    private QuestionGenerationTaskService questionGenerationTaskService;

    /**
     * V2: 创建异步生成任务（Phase 1）
     */
    @PostMapping("/question/v2/tasks")
    public ResponseEntity<?> createQuestionTask(@RequestBody Map<String, Object> requestData) {
        try {
            if (requestData == null || requestData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "请求数据不能为空"
                ));
            }

            Integer conversationId = null;
            if (requestData.get("conversationId") instanceof Number number) {
                conversationId = number.intValue();
            }
            String subject = (String) requestData.get("subject");
            String grade = requestData.get("grade") == null ? null : requestData.get("grade").toString();
            String difficulty = (String) requestData.get("difficulty");
            String questionType = (String) requestData.get("questionType");
            String questionCount = requestData.get("questionCount") == null ? null : requestData.get("questionCount").toString();
            String customMessage = requestData.get("customMessage") == null ? null : requestData.get("customMessage").toString();

            Map<String, Object> result = questionGenerationTaskService.createGenerationTask(
                    subject, grade, difficulty, questionType, questionCount, customMessage, conversationId
            );
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建题目任务异常: ", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * V2: 查询任务状态（Phase 1）
     */
    @GetMapping("/question/v2/tasks/{taskId}")
    public ResponseEntity<?> getQuestionTaskStatus(@PathVariable Long taskId) {
        try {
            Map<String, Object> result = questionGenerationTaskService.getGenerationTaskStatus(taskId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询题目任务状态异常: ", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 创建新的对话
     * @return 响应实体，包含新创建的对话信息或错误信息
     */

    @PostMapping("/newConversation")
    public ResponseEntity<?> createConversation() {
        try {
            Map<String, Object> result = teachingPlanQueService.createConversation();

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
            List<Map<String, Object>> conversations = teachingPlanQueService.getUserConversations();
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
            Map<String, Object> result = teachingPlanQueService.getConversationDetail(conversationId);

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
            Map<String, Object> result = teachingPlanQueService.deleteConversation(conversationId);

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
