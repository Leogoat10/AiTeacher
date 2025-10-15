package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.StuDto;
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
@RequestMapping("/student")
public class StudentController {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);
    
    @Autowired
    private AssignmentService assignmentService;
    
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
}
