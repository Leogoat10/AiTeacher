package com.leo.aiteacher.controller;

import com.leo.aiteacher.service.ExamPaperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/teacher/exam-paper/v1")
public class ExamPaperController {

    private static final Logger logger = LoggerFactory.getLogger(ExamPaperController.class);

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @GetMapping("/preset-prompts")
    public ResponseEntity<?> listPresetPrompts() {
        try {
            Map<String, Object> result = examPaperService.listPresetPrompts();
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("获取试卷预设提示词异常", e);
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
            Map<String, Object> result = examPaperService.createPresetPrompt(title, promptContent);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建试卷预设提示词异常", e);
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
            Map<String, Object> result = examPaperService.deletePresetPrompt(presetId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("删除试卷预设提示词异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createExamPaperTask(@RequestBody Map<String, Object> requestData) {
        try {
            if (requestData == null || requestData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "请求数据不能为空"
                ));
            }
            String subject = stringValue(requestData.get("subject"));
            String grade = stringValue(requestData.get("grade"));
            String examType = stringValue(requestData.get("examType"));
            Integer durationMinutes = parseOptionalInteger(requestData.get("durationMinutes"));
            Integer totalScore = parseOptionalInteger(requestData.get("totalScore"));
            Integer questionCount = parseOptionalInteger(requestData.get("questionCount"));
            Map<String, Integer> questionTypeCounts = parseQuestionTypeCounts(requestData.get("questionTypeCounts"));
            String difficulty = stringValue(requestData.get("difficulty"));
            String knowledgePoints = stringValue(requestData.get("knowledgePoints"));
            String customRequirement = stringValue(requestData.get("customRequirement"));

            Map<String, Object> result = examPaperService.createExamPaperTask(
                    subject, grade, examType, durationMinutes, totalScore, questionCount,
                    questionTypeCounts, difficulty, knowledgePoints, customRequirement
            );
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("创建试卷任务异常", e);
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
            Map<String, Object> result = examPaperService.getExamPaperTaskStatus(taskId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询试卷任务状态异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listExamPapers(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer pageSize) {
        try {
            Map<String, Object> result = examPaperService.listExamPapers(page, pageSize);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询试卷列表异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{paperId}")
    public ResponseEntity<?> getExamPaperDetail(@PathVariable Long paperId) {
        try {
            Map<String, Object> result = examPaperService.getExamPaperDetail(paperId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("查询试卷详情异常", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "内部错误",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{paperId}")
    public ResponseEntity<?> deleteExamPaper(@PathVariable Long paperId) {
        try {
            Map<String, Object> result = examPaperService.deleteExamPaper(paperId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            int status = result.containsKey("status") ? (int) result.get("status") : 500;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            logger.error("删除试卷异常", e);
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

    private Map<String, Integer> parseQuestionTypeCounts(Object rawValue) {
        java.util.LinkedHashMap<String, Integer> defaults = new java.util.LinkedHashMap<>();
        defaults.put("选择题", 0);
        defaults.put("填空题", 0);
        defaults.put("判断题", 0);
        defaults.put("简答题", 0);
        defaults.put("解答题", 0);
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return defaults;
        }
        for (String key : defaults.keySet()) {
            Object value = rawMap.get(key);
            if (value instanceof Number number) {
                defaults.put(key, Math.max(0, number.intValue()));
            } else if (value != null) {
                try {
                    defaults.put(key, Math.max(0, Integer.parseInt(value.toString())));
                } catch (NumberFormatException ignore) {
                    defaults.put(key, 0);
                }
            }
        }
        return defaults;
    }
}
