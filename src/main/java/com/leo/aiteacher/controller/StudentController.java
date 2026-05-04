package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.service.AssignmentService;
import com.leo.aiteacher.service.StudentAnswerService;
import com.leo.aiteacher.util.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/student")
public class StudentController {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);
    
    @Autowired
    private AssignmentService assignmentService;
    
    @Autowired
    private StudentAnswerService studentAnswerService;
    
    /**
     * 学生查看自己收到的题目列表
     * @return 题目列表
     */
    @GetMapping("/assignments")
    public ResponseEntity<?> getMyAssignments() {
        StuDto student = SessionUtils.getCurrentStudent();
        if (student == null) {
            logger.warn("未登录或会话失效，无法查询题目");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("学生查询收到的题目，studentId={}", student.getStudentId());
            List<Map<String, Object>> assignments = assignmentService.getStudentAssignments(student.getStudentId());
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("查询学生题目异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 学生提交答案
     * @param requestData 包含assignmentId和answer的请求数据
     * @return 提交结果、AI评分和分析
     */
    @PostMapping("/submitAnswer")
    public ResponseEntity<?> submitAnswer(@RequestBody Map<String, Object> requestData) {
        StuDto student = SessionUtils.getCurrentStudent();
        if (student == null) {
            logger.warn("未登录或会话失效，无法提交答案");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            Integer assignmentId = parseInteger(requestData.get("assignmentId"));
            String answer = requestData.get("answer") == null ? "" : String.valueOf(requestData.get("answer"));
            String imageDataUrl = requestData.get("imageDataUrl") == null ? null : String.valueOf(requestData.get("imageDataUrl"));
            
            if (assignmentId == null) {
                return ResponseEntity.badRequest().body("题目ID不能为空");
            }
            if ((answer == null || answer.trim().isEmpty()) && (imageDataUrl == null || imageDataUrl.isBlank())) {
                return ResponseEntity.badRequest().body("答案或图片不能为空");
            }
            
            logger.info("学生提交答案，studentId={}, assignmentId={}", student.getStudentId(), assignmentId);
            Map<String, Object> result = studentAnswerService.submitAnswer(assignmentId, student.getStudentId(), answer, imageDataUrl);
            
            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (Exception e) {
            logger.error("提交答案异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("提交答案失败: " + e.getMessage());
        }
    }

    /**
     * 学生上传图片后，先进行OCR识别并回填答案框
     * @param requestData 包含imageDataUrl
     * @return OCR识别结果
     */
    @PostMapping("/ocrAnswerImage")
    public ResponseEntity<?> ocrAnswerImage(@RequestBody Map<String, Object> requestData) {
        StuDto student = SessionUtils.getCurrentStudent();
        if (student == null) {
            logger.warn("未登录或会话失效，无法进行图片识别");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }

        try {
            String imageDataUrl = requestData.get("imageDataUrl") == null ? null : String.valueOf(requestData.get("imageDataUrl"));
            if (imageDataUrl == null || imageDataUrl.isBlank()) {
                return ResponseEntity.badRequest().body("图片不能为空");
            }
            Map<String, Object> result = studentAnswerService.recognizeAnswerImage(imageDataUrl);
            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("图片识别异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("图片识别失败: " + e.getMessage());
        }
    }

    /**
     * 学生查询某个题目的判题状态
     * @param assignmentId 题目ID
     * @return 判题状态
     */
    @GetMapping("/answer/{assignmentId}/status")
    public ResponseEntity<?> getAnswerStatus(@PathVariable Integer assignmentId) {
        StuDto student = SessionUtils.getCurrentStudent();
        if (student == null) {
            logger.warn("未登录或会话失效，无法查询答题状态");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }

        try {
            Map<String, Object> result = studentAnswerService.getAnswerStatus(assignmentId, student.getStudentId());
            if (result.containsKey("success") && (Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (Exception e) {
            logger.error("查询答题状态异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询答题状态失败: " + e.getMessage());
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer intVal) {
            return intVal;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
