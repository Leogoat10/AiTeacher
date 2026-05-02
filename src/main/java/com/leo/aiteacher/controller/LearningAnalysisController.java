package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.service.LearningAnalysisService;
import com.leo.aiteacher.util.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/learningAnalysis")
public class LearningAnalysisController {

    private static final Logger logger = LoggerFactory.getLogger(LearningAnalysisController.class);

    private final LearningAnalysisService learningAnalysisService;

    public LearningAnalysisController(LearningAnalysisService learningAnalysisService) {
        this.learningAnalysisService = learningAnalysisService;
    }

    @GetMapping("/course/{courseCode}/overview")
    public ResponseEntity<?> getCourseLearningAnalysis(@PathVariable String courseCode,
                                                       @RequestParam(required = false) Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法进行学情分析");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            Map<String, Object> result = learningAnalysisService.getCourseLearningAnalysis(courseCode, teacher.getTeacherId(), assignmentId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("课程学情分析异常，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("学情分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/course/{courseCode}/students/{studentId}")
    public ResponseEntity<?> getStudentLearningProfile(@PathVariable String courseCode,
                                                       @PathVariable Integer studentId,
                                                       @RequestParam(required = false) Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学生学情画像");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            Map<String, Object> result = learningAnalysisService.getStudentLearningProfile(studentId, courseCode, teacher.getTeacherId(), assignmentId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("学生学情画像查询异常，courseCode={}, studentId={}, teacherId={}",
                    courseCode, studentId, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/course/{courseCode}/logs")
    public ResponseEntity<?> listAnalysisLogs(@PathVariable String courseCode,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询学情分析日志");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            Map<String, Object> result = learningAnalysisService.listAnalysisLogs(courseCode, teacher.getTeacherId(), limit, assignmentId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("查询学情分析日志异常，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/course/{courseCode}/student-list")
    public ResponseEntity<?> listStudentsForAnalysis(@PathVariable String courseCode,
                                                     @RequestParam(required = false) Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询分析学生列表");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            List<Map<String, Object>> students = learningAnalysisService.listStudentsForAnalysis(courseCode, teacher.getTeacherId(), assignmentId);
            return ResponseEntity.ok(students);
        } catch (Exception e) {
            logger.error("查询分析学生列表异常，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/course/{courseCode}/analyze")
    public ResponseEntity<?> runManualAnalysis(@PathVariable String courseCode,
                                               @RequestBody Map<String, Object> requestData) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法触发学情分析");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            Integer assignmentId = parseOptionalInteger(requestData.get("assignmentId"));
            @SuppressWarnings("unchecked")
            List<Integer> studentIds = (List<Integer>) requestData.get("studentIds");
            Map<String, Object> result = learningAnalysisService.runManualAnalysis(
                    courseCode, teacher.getTeacherId(), assignmentId, studentIds
            );
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("触发手动学情分析异常，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/course/{courseCode}/assignment/{assignmentId}/saved")
    public ResponseEntity<?> listSavedStudentAnalyses(@PathVariable String courseCode,
                                                      @PathVariable Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询已保存学情分析");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            List<Map<String, Object>> data = learningAnalysisService.listSavedStudentAnalyses(
                    courseCode, teacher.getTeacherId(), assignmentId
            );
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("查询已保存学情分析异常，courseCode={}, assignmentId={}, teacherId={}",
                    courseCode, assignmentId, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/course/{courseCode}/assignment/{assignmentId}/latest")
    public ResponseEntity<?> getLatestSavedAnalysisResult(@PathVariable String courseCode,
                                                          @PathVariable Integer assignmentId) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.warn("未登录或会话失效，无法查询已分析结果");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        try {
            Map<String, Object> result = learningAnalysisService.getLatestSavedAnalysisResult(
                    courseCode, teacher.getTeacherId(), assignmentId
            );
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } catch (Exception e) {
            logger.error("查询最近分析结果异常，courseCode={}, assignmentId={}, teacherId={}",
                    courseCode, assignmentId, teacher.getTeacherId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("查询失败: " + e.getMessage());
        }
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
