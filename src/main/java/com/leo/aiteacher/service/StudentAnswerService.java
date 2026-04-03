package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface StudentAnswerService {
    
    /**
     * 学生提交答案并获取AI评分和分析
     * @param assignmentId 题目ID
     * @param studentId 学生ID
     * @param studentAnswer 学生答案
     * @return 包含评分和分析结果的Map
     */
    Map<String, Object> submitAnswer(Integer assignmentId, Integer studentId, String studentAnswer);

    /**
     * 查询当前学生某题目的判题状态
     * @param assignmentId 题目ID
     * @param studentId 学生ID
     * @return 状态信息
     */
    Map<String, Object> getAnswerStatus(Integer assignmentId, Integer studentId);
    
    /**
     * 教师查看某课程下所有学生的答题记录
     * @param courseCode 课程代码
     * @param teacherId 教师ID（用于权限验证）
     * @return 学生答题记录列表
     */
    List<Map<String, Object>> getCourseStudentAnswers(String courseCode, Integer teacherId);
    
    /**
     * 教师查看某课程下的学生列表（包含答题数量）
     * @param courseCode 课程代码
     * @param teacherId 教师ID（用于权限验证）
     * @return 学生列表
     */
    List<Map<String, Object>> getCourseStudents(String courseCode, Integer teacherId);
    
    /**
     * 教师查看某学生在某课程下的答题历史
     * @param studentId 学生ID
     * @param courseCode 课程代码
     * @param teacherId 教师ID（用于权限验证）
     * @return 学生答题记录列表
     */
    List<Map<String, Object>> getStudentAnswerHistory(Integer studentId, String courseCode, Integer teacherId);
    
    /**
     * 教师查看某个题目的所有学生答题情况
     * @param assignmentId 题目ID
     * @param teacherId 教师ID（用于权限验证）
     * @return 学生答题记录列表
     */
    List<Map<String, Object>> getAssignmentStudentAnswers(Integer assignmentId, Integer teacherId);
    
    /**
     * 教师更新学生答案的评分和分析
     * @param answerId 答案ID
     * @param teacherId 教师ID（用于权限验证）
     * @param score 新的评分
     * @param analysis 新的分析
     * @return 更新结果
     */
    Map<String, Object> updateStudentAnswer(Integer answerId, Integer teacherId, String score, String analysis);

    /**
     * 教师触发AI重新判题
     * @param answerId 答案ID
     * @param teacherId 教师ID（用于权限验证）
     * @return 重判任务信息
     */
    Map<String, Object> regradeAnswer(Integer answerId, Integer teacherId);

    /**
     * 教师查询某条答案的判题状态
     * @param answerId 答案ID
     * @param teacherId 教师ID（用于权限验证）
     * @return 状态信息
     */
    Map<String, Object> getAnswerStatusForTeacher(Integer answerId, Integer teacherId);
}
