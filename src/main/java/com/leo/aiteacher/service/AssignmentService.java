package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface AssignmentService {
    
    /**
     * 老师从聊天记录中选择题目并发送给指定课程的学生
     * @param messageId 消息ID（如果有）
     * @param content 题目内容
     * @param courseCode 课程代码
     * @param teacherId 教师ID
     * @param title 题目标题
     * @return 发送结果
     */
    Map<String, Object> sendAssignmentToCourse(Integer messageId, String content, String courseCode, Integer teacherId, String title);
    
    /**
     * 获取学生收到的题目列表
     * @param studentId 学生ID
     * @return 题目列表
     */
    List<Map<String, Object>> getStudentAssignments(Integer studentId);
    
    /**
     * 获取课程的题目列表
     * @param courseCode 课程代码
     * @return 题目列表
     */
    List<Map<String, Object>> getCourseAssignments(String courseCode);
}
