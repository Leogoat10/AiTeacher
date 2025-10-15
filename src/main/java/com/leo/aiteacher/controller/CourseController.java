package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.CourseDto;
import com.leo.aiteacher.pojo.dto.ImportResult;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.CourseMapper;
import com.leo.aiteacher.pojo.mapper.StudentMapper;
import com.leo.aiteacher.service.CourseService;
import com.leo.aiteacher.service.StudentImportService;
import com.leo.aiteacher.util.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/course")
public class CourseController {

    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentImportService studentImportService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private StudentMapper studentMapper;

    /**
     * 获取当前登录教师的课程列表
     * @return 教师课程列表
     */
    @GetMapping("/teacherCourse")
    public ResponseEntity<List<CourseDto>> getTeacherCourse() {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.info("未登录或会话失效，无法获取教师课程");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        logger.info("查询教师课程，teacherId={}", teacher.getTeacherId());
        List<CourseDto> courses = courseService.getCoursesByTeacherId(teacher.getTeacherId());
        return ResponseEntity.ok(courses);
    }

    /**
     * 添加课程
     * @param courseDto 课程信息
     * @return 添加结果
     */
    @PostMapping("/addCourse")
    public ResponseEntity<String> addCourse(@RequestBody CourseDto courseDto) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.info("未登录或会话失效，无法添加课程");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        logger.info("添加课程，courseId={}, teacherId={}", courseDto.getCourseCode(), teacher.getTeacherId());
        boolean success = courseService.addCourse(courseDto, teacher.getTeacherId());
        if (success) {
            return ResponseEntity.ok("课程添加成功");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("课程添加失败");
        }
    }

    /**
     * 移除课程
     * @param courseDto 课程信息
     * @return 添加结果
     */
    @PostMapping("/removeCourse")
    public ResponseEntity<String> removeCourse(@RequestBody CourseDto courseDto) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.info("未登录或会话失效，无法移除课程");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        logger.info("移除课程，courseId={}, teacherId={}", courseDto.getCourseCode(), teacher.getTeacherId());
        boolean success = courseService.removeCourse(courseDto.getCourseCode(), teacher.getTeacherId());
        if (success) {
            return ResponseEntity.ok("课程移除成功");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("课程移除失败");
        }
    }

    /**
     * 导入学生
     * @param courseCode 课程代码
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    @PostMapping("/importStudents")
    public ResponseEntity<?> importStudents(@RequestParam("courseCode") String courseCode,
                                            @RequestParam("file") MultipartFile file) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.info("未登录或会话失效，无法导入学生");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        logger.info("导入学生，courseCode={}, teacherId={}", courseCode, teacher.getTeacherId());
        ImportResult result = studentImportService.importStudents(courseCode, teacher.getTeacherId(), file);
        if ("导入失败".equals(result.getMessage())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 查询课程学生列表
     * @param courseCode 课程代码
     * @return 学生列表
     */
    @GetMapping("/students")
    public ResponseEntity<?> listCourseStudents(@RequestParam("courseCode") String courseCode) {
        TeacherDto teacher = SessionUtils.getCurrentTeacher();
        if (teacher == null) {
            logger.info("未登录或会话失效，无法查询课程学生");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录或会话失效");
        }
        Integer owned = courseMapper.countByCourseCodeAndTeacherId(courseCode, teacher.getTeacherId());
        if (owned == null || owned == 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("课程不存在或无权限");
        }
        List<StuDto> list = studentMapper.listByCourseCode(courseCode);
        return ResponseEntity.ok(list);
    }

}