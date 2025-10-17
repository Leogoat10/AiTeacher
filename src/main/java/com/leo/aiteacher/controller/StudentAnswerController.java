package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.TeacherDto;
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
@RequestMapping("/studentAnswer")
public class StudentAnswerController {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentAnswerController.class);
    
    @Autowired
    private StudentAnswerService studentAnswerService;
    
    /**
     * 教师查看某课程下所有学生的答题记录
     * @param courseCode 课程代码
     * @return 学生答题记录列表
     */
    @GetMapping("/course/{courseCode}")
    public ResponseEntity<?> getCourseStudentAnswers(@PathVariable String courseCode) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学生答题记录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("教师查询课程学生答题记录，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId());
            List<Map<String, Object>> answers = studentAnswerService.getCourseStudentAnswers(courseCode, teacher.getTeacherId());
            return ResponseEntity.ok(answers);
        } catch (Exception e) {
            logger.error("查询学生答题记录异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 教师查看某课程下的学生列表（包含答题数量）
     * @param courseCode 课程代码
     * @return 学生列表
     */
    @GetMapping("/course/{courseCode}/students")
    public ResponseEntity<?> getCourseStudents(@PathVariable String courseCode) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学生列表");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("教师查询课程学生列表，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId());
            List<Map<String, Object>> students = studentAnswerService.getCourseStudents(courseCode, teacher.getTeacherId());
            return ResponseEntity.ok(students);
        } catch (Exception e) {
            logger.error("查询学生列表异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 教师查看某学生在某课程下的答题历史
     * @param studentId 学生ID
     * @param courseCode 课程代码
     * @return 学生答题记录列表
     */
    @GetMapping("/student/{studentId}/course/{courseCode}")
    public ResponseEntity<?> getStudentAnswerHistory(
            @PathVariable Integer studentId,
            @PathVariable String courseCode) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学生答题历史");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("教师查询学生答题历史，studentId={}, courseCode={}, teacherId={}", 
                    studentId, courseCode, teacher.getTeacherId());
            List<Map<String, Object>> answers = studentAnswerService.getStudentAnswerHistory(
                    studentId, courseCode, teacher.getTeacherId());
            return ResponseEntity.ok(answers);
        } catch (Exception e) {
            logger.error("查询学生答题历史异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 教师查看某个题目的所有学生答题情况
     * @param assignmentId 题目ID
     * @return 学生答题记录列表
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<?> getAssignmentStudentAnswers(@PathVariable Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学生答题记录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            logger.info("教师查询题目学生答题记录，assignmentId={}, teacherId={}", assignmentId, teacher.getTeacherId());
            List<Map<String, Object>> answers = studentAnswerService.getAssignmentStudentAnswers(assignmentId, teacher.getTeacherId());
            return ResponseEntity.ok(answers);
        } catch (Exception e) {
            logger.error("查询学生答题记录异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 教师更新学生答案的评分和分析
     * @param answerId 答案ID
     * @param requestData 包含 score 和 analysis
     * @return 更新结果
     */
    @PutMapping("/{answerId}")
    public ResponseEntity<?> updateStudentAnswer(
            @PathVariable Integer answerId,
            @RequestBody Map<String, String> requestData) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法更新学生答案");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        
        try {
            String score = requestData.get("score");
            String analysis = requestData.get("analysis");
            
            if (score == null || analysis == null) {
                logger.warn("请求参数不完整");
                return ResponseEntity.badRequest().body("请求参数不完整");
            }
            
            logger.info("教师更新学生答案，answerId={}, teacherId={}", answerId, teacher.getTeacherId());
            Map<String, Object> result = studentAnswerService.updateStudentAnswer(
                    answerId, teacher.getTeacherId(), score, analysis);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            
        } catch (Exception e) {
            logger.error("更新学生答案异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("更新失败: " + e.getMessage());
        }
    }
}
