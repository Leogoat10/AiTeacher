package com.leo.aiteacher.service;

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
}
