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
            
            if (courseCode == null || title == null || content == null) {
                logger.warn("请求参数不完整: courseCode={}, title={}, content={}", courseCode, title, content != null ? "存在" : "null");
                return ResponseEntity.badRequest().body("请求参数不完整");
            }
            
            logger.info("老师发送题目，teacherId={}, courseCode={}, title={}", 
                    teacher.getTeacherId(), courseCode, title);
            
            Map<String, Object> result = assignmentService.sendAssignmentToCourse(
                    messageId, content, courseCode, teacher.getTeacherId(), title);
            
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
}
