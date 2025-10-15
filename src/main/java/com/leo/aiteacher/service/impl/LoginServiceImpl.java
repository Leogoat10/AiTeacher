package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.pojo.dto.LoginDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.mapper.StudentMapper;
import com.leo.aiteacher.pojo.mapper.TeacherMapper;
import com.leo.aiteacher.service.LoginService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginServiceImpl implements LoginService {
    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);

    @Resource
    private TeacherMapper teacherMapper;
    @Autowired
    private StudentMapper studentMapper;

    @Override
    public TeacherDto teacherLogin(LoginDto loginDto, HttpServletResponse response) {
        if (loginDto == null) {
            return null;
        }

        TeacherDto teacher = null;

        // 根据输入内容查询教师（可作为ID或用户名）
        if (loginDto.getUserId() != null && !loginDto.getUserId().isEmpty()) {
            // 先尝试按教师ID查询
            teacher = teacherMapper.getTeacherById(loginDto.getUserId());

            // 如果通过ID未找到，则尝试按用户名查询
            if (teacher == null) {
                teacher = teacherMapper.getTeacherByName(loginDto.getUserId());
            }
        } else {
            log.warn("未提供有效的登录凭据");
            return null;
        }

        // 验证密码
        if (teacher == null) {
            log.warn("未找到对应的教师信息");
            return null;
        }

        if (!teacher.getPassword().equals(loginDto.getPassWord())) {
            log.warn("教师密码验证失败，教师ID: {}", teacher.getTeacherId());
            return null;
        }

        return teacher;
    }


    @Override
    public boolean teacherRegister(LoginDto loginDto) {
        if (loginDto == null || loginDto.getUserName() == null || loginDto.getUserName().isEmpty()
                || loginDto.getPassWord() == null || loginDto.getPassWord().isEmpty()) {
            log.warn("注册信息不完整");
            return false;
        }

        // 检查用户名是否已存在
        TeacherDto existingTeacher = teacherMapper.getTeacherByName(loginDto.getUserName());
        if (existingTeacher != null) {
            log.warn("用户名已存在: {}", loginDto.getUserName());
            return false;
        }

        // 创建新教师对象
        TeacherDto newTeacher = new TeacherDto();
        newTeacher.setTeacherId((int)(Math.random() * 900000 + 100000)); // 生成6位随机数作为教师ID
        newTeacher.setTeacherName(loginDto.getUserName());
        newTeacher.setPassword(loginDto.getPassWord());

        // 插入数据库
        int rowsAffected = teacherMapper.insertTeacher(newTeacher);
        if (rowsAffected > 0) {
            log.info("教师注册成功: {}", loginDto.getUserName());
            return true;
        } else {
            log.error("教师注册失败: {}", loginDto.getUserName());
            return false;
        }
    }

    @Override
    public StuDto studentLogin(LoginDto loginDto, HttpServletResponse response) {
        if (loginDto == null) {
            return null;
        }

        StuDto student = null;

        // 根据输入内容查询教师（可作为ID或用户名）
        if (loginDto.getUserId() != null && !loginDto.getUserId().isEmpty()) {
            // 先尝试按ID查询
            student = studentMapper.getStudentById(Integer.valueOf(loginDto.getUserId()));

            // 如果通过ID未找到，则尝试按用户名查询
            if (student == null) {
                student = studentMapper.getStudentByName(loginDto.getUserId());
            }
        } else {
            log.warn("未提供有效的登录凭据");
            return null;
        }

        // 验证密码
        if (student == null) {
            log.warn("未找到对应的学生信息");
            return null;
        }

        if (!student.getPassword().equals(loginDto.getPassWord())) {
            log.warn("学生密码验证失败，教师ID: {}", student.getStudentId());
            return null;
        }

        return student;
    }

    @Override
    public boolean studentRegister(LoginDto loginDto) {
        if (loginDto == null || loginDto.getUserName() == null || loginDto.getUserName().isEmpty()
                || loginDto.getPassWord() == null || loginDto.getPassWord().isEmpty()) {
            log.warn("注册信息不完整");
            return false;
        }

        // 检查用户名是否已存在
        StuDto existingStudent = studentMapper.getStudentByName(loginDto.getUserName());
        if (existingStudent != null) {
            log.warn("用户名已存在: {}", loginDto.getUserName());
            return false;
        }

        // 创建新教师对象
        StuDto newStudent = new StuDto();
        newStudent.setStudentId((int)(Math.random() * 900000 + 100000)); // 生成6位随机数作为学生ID
        newStudent.setStudentName(loginDto.getUserName());
        newStudent.setPassword(loginDto.getPassWord());

        // 插入数据库
        int rowsAffected = studentMapper.insertStudent(newStudent);
        if (rowsAffected > 0) {
            log.info("学生注册成功: {}", loginDto.getUserName());
            return true;
        } else {
            log.error("学生注册失败: {}", loginDto.getUserName());
            return false;
        }
    }


}
