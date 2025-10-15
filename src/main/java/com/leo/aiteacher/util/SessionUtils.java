package com.leo.aiteacher.util;

import com.leo.aiteacher.pojo.dto.TeacherDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class SessionUtils {

    private static final Logger logger = LoggerFactory.getLogger(SessionUtils.class);

    public static TeacherDto getCurrentTeacher() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                logger.warn("无法获取请求上下文");
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            if (request.getSession(false) != null) {
                Object obj = request.getSession(false).getAttribute("teacher");
                if (obj instanceof TeacherDto) {
                    return (TeacherDto) obj;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("获取当前教师信息失败: ", e);
            return null;
        }
    }

    public static StuDto getCurrentStudent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                logger.warn("无法获取请求上下文");
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            if (request.getSession(false) != null) {
                Object obj = request.getSession(false).getAttribute("student");
                if (obj instanceof StuDto) {
                    return (StuDto) obj;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("获取当前学生信息失败: ", e);
            return null;
        }
    }
}