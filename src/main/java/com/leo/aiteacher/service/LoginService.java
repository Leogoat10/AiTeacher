package com.leo.aiteacher.service;

import com.leo.aiteacher.pojo.dto.LoginDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.TeacherDto;
import jakarta.servlet.http.HttpServletResponse;

public interface LoginService  {
    TeacherDto teacherLogin(LoginDto loginDto, HttpServletResponse response);

    boolean teacherRegister(LoginDto loginDto);

    StuDto studentLogin(LoginDto loginDto, HttpServletResponse response);

    boolean studentRegister(LoginDto loginDto);
}
