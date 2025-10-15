// src/main/java/com/leo/aiteacher/controller/LoginController.java
package com.leo.aiteacher.controller;

import cn.hutool.core.map.MapUtil;
import com.leo.aiteacher.pojo.dto.LoginDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/login")
public class LoginController {
    @Autowired
    private LoginService loginService;

    // 添加日志记录器
    private static final Logger logger = LoggerFactory.getLogger(AiTeacherController.class);

    /**
     * 教师登录接口
     * @param loginDto 包含用户名和密码的登录数据传输对象
     * @param request  当前的 HTTP 请求对象
     * @param response 当前的 HTTP 响应对象
     * @return 响应实体，包含登录结果或错误信息
     */
    @PostMapping("/teacherLogin")
    public ResponseEntity<?> teacherLogin(@Validated @RequestBody LoginDto loginDto, HttpServletRequest request, HttpServletResponse response) {
        TeacherDto teacher = loginService.teacherLogin(loginDto, response);
        if (teacher == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("账号或密码错误");
        }
        // 将用户信息存入 session
        request.getSession().setAttribute("teacher", teacher);
        // 返回用户的基本信息
        Map<Object, Object> userInfo = MapUtil.builder()
                .put("teacherId", teacher.getTeacherId())
                .put("teacherName", teacher.getTeacherName())
                .map();

        logger.info("老师登录成功：{}", userInfo);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 教师注册接口
     * @param loginDto 包含用户名和密码的注册数据传输对象
     * @return 响应实体，包含注册结果或错误信息
     */
    @PostMapping("/teacherRegister")
    public ResponseEntity<?> teacherRegister(@Validated @RequestBody LoginDto loginDto) {
        boolean success = loginService.teacherRegister(loginDto);
        if (success) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("注册失败，用户名可能已存在");
        }
    }

    /**
     * 学生登录接口
     * @param loginDto 包含用户名和密码的登录数据传输对象
     * @param request  当前的 HTTP 请求对象
     * @param response 当前的 HTTP 响应对象
     * @return 响应实体，包含登录结果或错误信息
     */
    @PostMapping("/studentLogin")
    public ResponseEntity<?> studentLogin(@Validated @RequestBody LoginDto loginDto, HttpServletRequest request, HttpServletResponse response) {
        StuDto student = loginService.studentLogin(loginDto, response);
        if (student == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("账号或密码错误");
        }
        // 将用户信息存入 session
        request.getSession().setAttribute("student", student);
        // 返回用户的基本信息
        Map<Object, Object> userInfo = MapUtil.builder()
                .put("studentId", student.getStudentId())
                .put("studentName", student.getStudentName())
                .map();

        logger.info("学生登录成功：{}", userInfo);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 学生注册接口
     * @param loginDto 包含用户名和密码的注册数据传输对象
     * @return 响应实体，包含注册结果或错误信息
     */
    @PostMapping("/studentRegister")
    public ResponseEntity<?> studentRegister(@Validated @RequestBody LoginDto loginDto) {
        boolean success = loginService.studentRegister(loginDto);
        if (success) {
            return ResponseEntity.ok(true);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("注册失败，用户名可能已存在");
        }
    }

    /**
     * 教师学生登出接口
     * @param request 当前的 HTTP 请求对象
     * @return 响应实体，表示登出成功
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return ResponseEntity.ok().body(true);
    }
}