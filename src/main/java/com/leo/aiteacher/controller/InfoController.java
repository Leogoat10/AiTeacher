// java
package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.leo.aiteacher.util.SessionUtils;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/Info")
public class InfoController {

    /**
     * 获取当前登录教师的信息
     * @return 教师信息的响应实体，若未登录则返回 401 状态
     */
    @GetMapping("/teacherInfo")
    public ResponseEntity<TeacherDto> getTeacherInfo() {
        TeacherDto current = SessionUtils.getCurrentTeacher();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(current);
    }

    /**
     * 获取当前登录学生的信息
     * @return 学生信息的响应实体，若未登录则返回 401 状态
     */
    @GetMapping("/studentInfo")
    public ResponseEntity<StuDto> getStudentInfo() {
        StuDto current = SessionUtils.getCurrentStudent();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(current);
    }
}