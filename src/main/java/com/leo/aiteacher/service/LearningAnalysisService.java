package com.leo.aiteacher.service;

import java.util.List;
import java.util.Map;

public interface LearningAnalysisService {

    Map<String, Object> getCourseLearningAnalysis(String courseCode, Integer teacherId, Integer assignmentId);

    Map<String, Object> getStudentLearningProfile(Integer studentId, String courseCode, Integer teacherId, Integer assignmentId);

    Map<String, Object> listAnalysisLogs(String courseCode, Integer teacherId, Integer limit, Integer assignmentId);

    List<Map<String, Object>> listStudentsForAnalysis(String courseCode, Integer teacherId, Integer assignmentId);

    Map<String, Object> runManualAnalysis(String courseCode, Integer teacherId, Integer assignmentId, List<Integer> studentIds);

    List<Map<String, Object>> listSavedStudentAnalyses(String courseCode, Integer teacherId, Integer assignmentId);

    Map<String, Object> getLatestSavedAnalysisResult(String courseCode, Integer teacherId, Integer assignmentId);
}
