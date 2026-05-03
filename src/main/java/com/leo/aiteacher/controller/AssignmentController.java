package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.service.AssignmentService;
import com.leo.aiteacher.util.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/assignment")
public class AssignmentController {
    
    private static final Logger logger = LoggerFactory.getLogger(AssignmentController.class);
    
    @Autowired
    private AssignmentService assignmentService;
    
    /**
     * 老师从聊天记录中选择题目并发送给指定课程的学生
     * @param requestData 包含 messageId(可选), content, courseCode, title
     * @return 发送结果
     */
    @PostMapping("/sendToCourse")
    public ResponseEntity<?> sendAssignmentToCourse(@RequestBody Map<String, Object> requestData) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法发送题目");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            Integer messageId = requestData.get("messageId") != null ? (Integer) requestData.get("messageId") : null;
            String content = (String) requestData.get("content");
            String courseCode = (String) requestData.get("courseCode");
            String title = (String) requestData.get("title");
            Integer totalScore = parseInteger(requestData.get("totalScore"));
            String questionStructureJson = requestData.get("questionStructureJson") == null
                    ? null
                    : String.valueOf(requestData.get("questionStructureJson"));
            
            if (courseCode == null || title == null || content == null) {
                logger.warn("请求参数不完整: courseCode={}, title={}, content={}", courseCode, title, content != null ? "存在" : "null");
                return ResponseEntity.badRequest().body("请求参数不完整");
            }
            
            logger.info("老师发送题目，teacherId={}, courseCode={}, title={}", 
                    teacher.getTeacherId(), courseCode, title);
            
            Map<String, Object> result = assignmentService.sendAssignmentToCourse(
                    messageId, content, courseCode, teacher.getTeacherId(), title, totalScore, questionStructureJson);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            
        } catch (Exception e) {
            logger.error("发送题目异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("发送题目失败: " + e.getMessage());
        }
    }

    /**
     * 批量发送题目给课程学生
     * @param requestData 包含courseCode和assignments数组
     * @return 批次发送结果
     */
    @PostMapping("/sendBatchToCourse")
    public ResponseEntity<?> sendBatchToCourse(@RequestBody Map<String, Object> requestData) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法批量发送题目");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }

        try {
            String courseCode = (String) requestData.get("courseCode");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) requestData.get("assignments");
            String sendBatchId = requestData.get("sendBatchId") == null
                    ? UUID.randomUUID().toString()
                    : String.valueOf(requestData.get("sendBatchId"));

            if (courseCode == null || courseCode.trim().isEmpty() || assignments == null || assignments.isEmpty()) {
                logger.warn("批量发送请求参数不完整: courseCode={}, assignments={}", courseCode, assignments == null ? "null" : assignments.size());
                return ResponseEntity.badRequest().body("请求参数不完整");
            }

            logger.info("老师批量发送题目，teacherId={}, courseCode={}, batchId={}, count={}",
                    teacher.getTeacherId(), courseCode, sendBatchId, assignments.size());

            Map<String, Object> result = assignmentService.sendAssignmentsBatchToCourse(
                    assignments, courseCode, teacher.getTeacherId(), sendBatchId);

            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (Exception e) {
            logger.error("批量发送题目异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("批量发送失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取课程的题目列表（教师端）
     * @param courseCode 课程代码
     * @return 题目列表
     */
    @GetMapping("/course/{courseCode}")
    public ResponseEntity<?> getCourseAssignments(@PathVariable String courseCode) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询课程题目");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("查询课程题目，courseCode={}", courseCode);
            List<Map<String, Object>> assignments = assignmentService.getCourseAssignments(courseCode);
            return ResponseEntity.ok(assignments);
        } catch (Exception e) {
            logger.error("查询课程题目异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
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
