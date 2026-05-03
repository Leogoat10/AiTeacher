// java
package com.leo.aiteacher.controller;

import com.leo.aiteacher.pojo.dto.PasswordChangeDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.StudentMapper;
import com.leo.aiteacher.pojo.mapper.TeacherMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.leo.aiteacher.util.SessionUtils;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/Info")
public class InfoController {
    @Resource
    private TeacherMapper teacherMapper;
    @Resource
    private StudentMapper studentMapper;

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

    @PostMapping("/teacherInfo/password")
    public ResponseEntity<?> changeTeacherPassword(@RequestBody PasswordChangeDto passwordChangeDto, HttpServletRequest request) {
        TeacherDto current = SessionUtils.getCurrentTeacher();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        if (passwordChangeDto == null
                || StringUtils.isBlank(passwordChangeDto.getOldPassword())
                || StringUtils.isBlank(passwordChangeDto.getNewPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("原始密码和新密码不能为空");
        }
        if (passwordChangeDto.getOldPassword().equals(passwordChangeDto.getNewPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("新密码不能与原始密码相同");
        }

        TeacherDto dbTeacher = teacherMapper.getTeacherById(String.valueOf(current.getTeacherId()));
        if (dbTeacher == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("教师不存在");
        }
        if (!passwordChangeDto.getOldPassword().equals(dbTeacher.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("原始密码错误");
        }

        int affectedRows = teacherMapper.updateTeacherPassword(current.getTeacherId(), passwordChangeDto.getNewPassword());
        if (affectedRows <= 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("修改密码失败");
        }

        current.setPassword(passwordChangeDto.getNewPassword());
        request.getSession().setAttribute("teacher", current);
        return ResponseEntity.ok(true);
    }

    @PostMapping("/studentInfo/password")
    public ResponseEntity<?> changeStudentPassword(@RequestBody PasswordChangeDto passwordChangeDto, HttpServletRequest request) {
        StuDto current = SessionUtils.getCurrentStudent();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        if (passwordChangeDto == null
                || StringUtils.isBlank(passwordChangeDto.getOldPassword())
                || StringUtils.isBlank(passwordChangeDto.getNewPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("原始密码和新密码不能为空");
        }
        if (passwordChangeDto.getOldPassword().equals(passwordChangeDto.getNewPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("新密码不能与原始密码相同");
        }

        StuDto dbStudent = studentMapper.getStudentById(current.getStudentId());
        if (dbStudent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("学生不存在");
        }
        if (!passwordChangeDto.getOldPassword().equals(dbStudent.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("原始密码错误");
        }

        int affectedRows = studentMapper.updateStudentPassword(current.getStudentId(), passwordChangeDto.getNewPassword());
        if (affectedRows <= 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("修改密码失败");
        }

        current.setPassword(passwordChangeDto.getNewPassword());
        request.getSession().setAttribute("student", current);
        return ResponseEntity.ok(true);
    }
}
