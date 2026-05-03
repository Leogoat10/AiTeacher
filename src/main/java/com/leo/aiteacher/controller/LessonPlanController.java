package com.leo.aiteacher.controller;

import com.leo.aiteacher.service.LessonPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/teacher/lesson-plan/v1")
public class LessonPlanController {

    private static final Logger logger = LoggerFactory.getLogger(LessonPlanController.class);

    private final LessonPlanService lessonPlanService;

    public LessonPlanController(LessonPlanService lessonPlanService) {
        this.lessonPlanService = lessonPlanService;
    }

    @PostMapping("/newConversation")
    public ResponseEntity<?> createConversation() {
        try {
            Map<String, Object> result = lessonPlanService.createConversation();
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建教案对话异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations() {
        try {
            Map<String, Object> result = lessonPlanService.getConversations();
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("获取教案对话列表异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/preset-prompts")
    public ResponseEntity<?> listPresetPrompts() {
        try {
            Map<String, Object> result = lessonPlanService.listPresetPrompts();
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("获取教案预设提示词异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/preset-prompts")
    public ResponseEntity<?> createPresetPrompt(@RequestBody Map<String, Object> requestData) {
        try {
            if (requestData == null || requestData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "请求数据不能为空"
                ));
            }
            String title = stringValue(requestData.get("title"));
            String promptContent = stringValue(requestData.get("promptContent"));
            Map<String, Object> result = lessonPlanService.createPresetPrompt(title, promptContent);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建教案预设提示词异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/preset-prompts/{presetId}")
    public ResponseEntity<?> deletePresetPrompt(@PathVariable Long presetId) {
        try {
            Map<String, Object> result = lessonPlanService.deletePresetPrompt(presetId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("删除教案预设提示词异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<?> getConversationDetail(@PathVariable Integer conversationId) {
        try {
            Map<String, Object> result = lessonPlanService.getConversationDetail(conversationId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("获取教案对话详情异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createLessonPlanTask(@RequestBody Map<String, Object> requestData) {
        try {
            if (requestData == null || requestData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "请求数据不能为空"
                ));
            }

            String subject = stringValue(requestData.get("subject"));
            String grade = stringValue(requestData.get("grade"));
            String teachingTopic = stringValue(requestData.get("teachingTopic"));
            Integer durationMinutes = parseOptionalInteger(requestData.get("durationMinutes"));
            Integer interactionCount = parseOptionalInteger(requestData.get("interactionCount"));
            String customRequirement = stringValue(requestData.get("customRequirement"));
            Integer conversationId = parseOptionalInteger(requestData.get("conversationId"));
            Boolean useContext = requestData.get("useContext") == null ? null : Boolean.valueOf(requestData.get("useContext").toString());
            Integer contextRounds = parseOptionalInteger(requestData.get("contextRounds"));

            Map<String, Object> result = lessonPlanService.createLessonPlanTask(
                    subject, grade, teachingTopic, durationMinutes, interactionCount, customRequirement,
                    conversationId, useContext, contextRounds
            );
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建教案任务异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable Long taskId) {
        try {
            Map<String, Object> result = lessonPlanService.getLessonPlanTaskStatus(taskId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询教案任务状态异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listLessonPlans(@RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer pageSize) {
        try {
            Map<String, Object> result = lessonPlanService.listLessonPlans(page, pageSize);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询教案列表异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{planId}")
    public ResponseEntity<?> getLessonPlanDetail(@PathVariable Long planId) {
        try {
            Map<String, Object> result = lessonPlanService.getLessonPlanDetail(planId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询教案详情异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<?> deleteLessonPlan(@PathVariable Long planId) {
        try {
            Map<String, Object> result = lessonPlanService.deleteLessonPlan(planId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("删除教案异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    private String stringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }

    private Integer parseOptionalInteger(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(rawValue.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
