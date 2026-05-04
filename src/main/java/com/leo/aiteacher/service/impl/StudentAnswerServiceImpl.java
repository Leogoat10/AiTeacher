package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.client.QwenVisionClient;
import com.leo.aiteacher.pojo.dto.AssignmentDto;
import com.leo.aiteacher.pojo.dto.GradingTaskDto;
import com.leo.aiteacher.pojo.dto.StudentAnswerDto;
import com.leo.aiteacher.pojo.mapper.AssignmentMapper;
import com.leo.aiteacher.pojo.mapper.GradingTaskMapper;
import com.leo.aiteacher.pojo.mapper.StudentAnswerMapper;
import com.leo.aiteacher.service.StudentAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAnswerServiceImpl implements StudentAnswerService {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentAnswerServiceImpl.class);
    
    @Autowired
    private StudentAnswerMapper studentAnswerMapper;
    
    @Autowired
    private AssignmentMapper assignmentMapper;

    @Autowired
    private GradingTaskMapper gradingTaskMapper;

    @Autowired
    private StudentGradingAsyncService studentGradingAsyncService;
    
    @Override
    public Map<String, Object> submitAnswer(Integer assignmentId, Integer studentId, String studentAnswer, String imageDataUrl) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            AssignmentDto assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                result.put("success", false);
                result.put("message", "题目不存在");
                return result;
            }
            
            StudentAnswerDto existingAnswer = studentAnswerMapper.getByAssignmentAndStudent(assignmentId, studentId);
            if (existingAnswer != null) {
                result.put("success", false);
                result.put("message", "已经提交过答案，不能重复提交");
                return result;
            }
            PreparedAnswer preparedAnswer = prepareAnswer(studentAnswer, imageDataUrl);
            if (preparedAnswer.finalAnswer() == null || preparedAnswer.finalAnswer().isBlank()) {
                result.put("success", false);
                result.put("message", "图片未识别到文字，请补充文字答案或重新上传清晰图片");
                return result;
            }

            StudentAnswerDto studentAnswerDto = new StudentAnswerDto();
            studentAnswerDto.setAssignmentId(assignmentId);
            studentAnswerDto.setStudentId(studentId);
            studentAnswerDto.setStudentAnswer(preparedAnswer.finalAnswer());
            studentAnswerDto.setAiScore(null);
            studentAnswerDto.setAiAnalysis(null);
            studentAnswerDto.setGradingStatus("PENDING");
            studentAnswerDto.setGradingError(null);
            studentAnswerMapper.insert(studentAnswerDto);

            GradingTaskDto gradingTask = new GradingTaskDto();
            gradingTask.setAnswerId(studentAnswerDto.getId());
            gradingTask.setStatus("PENDING");
            gradingTask.setRetryCount(0);
            gradingTaskMapper.insert(gradingTask);

            studentGradingAsyncService.processGradingTask(gradingTask.getId());

            logger.info("学生答案提交成功并进入异步判题，assignmentId={}, studentId={}, answerId={}, taskId={}",
                    assignmentId, studentId, studentAnswerDto.getId(), gradingTask.getId());

            result.put("success", true);
            result.put("message", "答案提交成功，AI正在批改");
            result.put("answerId", studentAnswerDto.getId());
            result.put("gradingTaskId", gradingTask.getId());
            result.put("gradingStatus", "PENDING");
            result.put("ocrApplied", preparedAnswer.ocrApplied());
            if (preparedAnswer.detectedText() != null && !preparedAnswer.detectedText().isBlank()) {
                result.put("ocrText", preparedAnswer.detectedText());
            }
            
        } catch (Exception e) {
            logger.error("提交答案失败", e);
            result.put("success", false);
            result.put("message", "提交答案失败: " + e.getMessage());
        }
        
        return result;
    }

    @Override
    public Map<String, Object> getAnswerStatus(Integer assignmentId, Integer studentId) {
        Map<String, Object> result = new HashMap<>();

        try {
            StudentAnswerDto answer = studentAnswerMapper.getByAssignmentAndStudent(assignmentId, studentId);
            if (answer == null) {
                result.put("success", false);
                result.put("message", "未找到答题记录");
                return result;
            }

            result.put("success", true);
            result.put("assignmentId", assignmentId);
            result.put("answerId", answer.getId());
            result.put("gradingStatus", answer.getGradingStatus() == null ? "PENDING" : answer.getGradingStatus());
            result.put("score", answer.getAiScore());
            result.put("analysis", answer.getAiAnalysis());
            result.put("gradingError", answer.getGradingError());
            result.put("evaluationJson", answer.getEvaluationJson());
            result.put("submittedAt", answer.getSubmittedAt());
            result.put("gradingCompletedAt", answer.getGradingCompletedAt());
            return result;
        } catch (Exception e) {
            logger.error("查询答题状态失败，assignmentId={}, studentId={}", assignmentId, studentId, e);
            result.put("success", false);
            result.put("message", "查询答题状态失败: " + e.getMessage());
            return result;
        }
    }
    
    @Override
    public List<Map<String, Object>> getCourseStudentAnswers(String courseCode, Integer teacherId) {
        logger.info("教师查看课程学生答题记录，courseCode={}, teacherId={}", courseCode, teacherId);
        
        // 验证教师是否有权限查看该课程
        AssignmentDto assignment = assignmentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AssignmentDto>()
                .eq("course_code", courseCode)
                .eq("teacher_id", teacherId)
                .last("LIMIT 1")
        );
        
        if (assignment == null) {
            logger.warn("教师无权限查看该课程的答题记录，courseCode={}, teacherId={}", courseCode, teacherId);
            return List.of();
        }
        
        return studentAnswerMapper.getCourseStudentAnswers(courseCode);
    }
    
    @Override
    public List<Map<String, Object>> getCourseStudents(String courseCode, Integer teacherId) {
        logger.info("教师查看课程学生列表，courseCode={}, teacherId={}", courseCode, teacherId);
        
        // 验证教师是否有权限查看该课程
        AssignmentDto assignment = assignmentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AssignmentDto>()
                .eq("course_code", courseCode)
                .eq("teacher_id", teacherId)
                .last("LIMIT 1")
        );
        
        if (assignment == null) {
            logger.warn("教师无权限查看该课程的学生列表，courseCode={}, teacherId={}", courseCode, teacherId);
            return List.of();
        }
        
        return studentAnswerMapper.getCourseStudents(courseCode);
    }
    
    @Override
    public List<Map<String, Object>> getStudentAnswerHistory(Integer studentId, String courseCode, Integer teacherId) {
        logger.info("教师查看学生答题历史，studentId={}, courseCode={}, teacherId={}", studentId, courseCode, teacherId);
        
        // 验证教师是否有权限查看该课程
        AssignmentDto assignment = assignmentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AssignmentDto>()
                .eq("course_code", courseCode)
                .eq("teacher_id", teacherId)
                .last("LIMIT 1")
        );
        
        if (assignment == null) {
            logger.warn("教师无权限查看该课程的学生答题历史，courseCode={}, teacherId={}", courseCode, teacherId);
            return List.of();
        }
        
        return studentAnswerMapper.getStudentAnswerHistory(studentId, courseCode);
    }
    
    @Override
    public List<Map<String, Object>> getAssignmentStudentAnswers(Integer assignmentId, Integer teacherId) {
        logger.info("教师查看题目学生答题记录，assignmentId={}, teacherId={}", assignmentId, teacherId);
        
        // 验证教师是否有权限查看该题目
        AssignmentDto assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null || !assignment.getTeacherId().equals(teacherId)) {
            logger.warn("教师无权限查看该题目的答题记录，assignmentId={}, teacherId={}", assignmentId, teacherId);
            return List.of();
        }
        
        return studentAnswerMapper.getAssignmentStudentAnswers(assignmentId);
    }
    
    @Override
    public Map<String, Object> updateStudentAnswer(Integer answerId, Integer teacherId, String score, String analysis) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 获取答案记录
            StudentAnswerDto answer = studentAnswerMapper.selectById(answerId);
            if (answer == null) {
                result.put("success", false);
                result.put("message", "答案记录不存在");
                return result;
            }
            
            // 2. 验证教师是否有权限修改该答案
            AssignmentDto assignment = assignmentMapper.selectById(answer.getAssignmentId());
            if (assignment == null || !assignment.getTeacherId().equals(teacherId)) {
                result.put("success", false);
                result.put("message", "无权限修改该答案");
                return result;
            }
            
            // 3. 更新评分和分析
            answer.setAiScore(score);
            answer.setAiAnalysis(analysis);
            answer.setGradingStatus("SUCCESS");
            answer.setGradingError(null);
            answer.setGradingCompletedAt(LocalDateTime.now());
            studentAnswerMapper.updateById(answer);
            
            logger.info("教师更新学生答案成功，answerId={}, teacherId={}", answerId, teacherId);
            
            result.put("success", true);
            result.put("message", "更新成功");
            
        } catch (Exception e) {
            logger.error("更新学生答案失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        
        return result;
    }

    @Override
    public Map<String, Object> regradeAnswer(Integer answerId, Integer teacherId) {
        Map<String, Object> result = new HashMap<>();

        try {
            StudentAnswerDto answer = studentAnswerMapper.selectById(answerId);
            if (answer == null) {
                result.put("success", false);
                result.put("message", "答案记录不存在");
                return result;
            }

            AssignmentDto assignment = assignmentMapper.selectById(answer.getAssignmentId());
            if (assignment == null || !assignment.getTeacherId().equals(teacherId)) {
                result.put("success", false);
                result.put("message", "无权限重判该答案");
                return result;
            }

            answer.setGradingStatus("PENDING");
            answer.setGradingError(null);
            answer.setAiScore(null);
            answer.setAiAnalysis(null);
            answer.setEvaluationJson(null);
            answer.setRawResponse(null);
            answer.setGradingStartedAt(null);
            answer.setGradingCompletedAt(null);
            studentAnswerMapper.updateById(answer);

            GradingTaskDto gradingTask = new GradingTaskDto();
            gradingTask.setAnswerId(answerId);
            gradingTask.setStatus("PENDING");
            gradingTask.setRetryCount(0);
            gradingTaskMapper.insert(gradingTask);

            studentGradingAsyncService.processGradingTask(gradingTask.getId());

            result.put("success", true);
            result.put("message", "已触发重新判题");
            result.put("answerId", answerId);
            result.put("gradingTaskId", gradingTask.getId());
            return result;
        } catch (Exception e) {
            logger.error("重新判题失败，answerId={}, teacherId={}", answerId, teacherId, e);
            result.put("success", false);
            result.put("message", "重新判题失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> getAnswerStatusForTeacher(Integer answerId, Integer teacherId) {
        Map<String, Object> result = new HashMap<>();

        try {
            StudentAnswerDto answer = studentAnswerMapper.selectById(answerId);
            if (answer == null) {
                result.put("success", false);
                result.put("message", "答案记录不存在");
                return result;
            }

            AssignmentDto assignment = assignmentMapper.selectById(answer.getAssignmentId());
            if (assignment == null || !assignment.getTeacherId().equals(teacherId)) {
                result.put("success", false);
                result.put("message", "无权限查看该答案状态");
                return result;
            }

            result.put("success", true);
            result.put("answerId", answer.getId());
            result.put("assignmentId", answer.getAssignmentId());
            result.put("gradingStatus", answer.getGradingStatus() == null ? "PENDING" : answer.getGradingStatus());
            result.put("score", answer.getAiScore());
            result.put("analysis", answer.getAiAnalysis());
            result.put("gradingError", answer.getGradingError());
            result.put("evaluationJson", answer.getEvaluationJson());
            result.put("gradingCompletedAt", answer.getGradingCompletedAt());
            return result;
        } catch (Exception e) {
            logger.error("教师查询判题状态失败，answerId={}, teacherId={}", answerId, teacherId, e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            return result;
        }
    }
    
    @Autowired
    private QwenVisionClient qwenVisionClient;

    private PreparedAnswer prepareAnswer(String studentAnswer, String imageDataUrl) throws Exception {
        String plainAnswer = studentAnswer == null ? "" : studentAnswer.trim();
        String normalizedImageDataUrl = imageDataUrl == null ? "" : imageDataUrl.trim();

        String detectedText = "";
        boolean ocrApplied = false;
        if (!normalizedImageDataUrl.isBlank()) {
            validateImageDataUrl(normalizedImageDataUrl);
            detectedText = qwenVisionClient.recognizeTextFromImageDataUrl(normalizedImageDataUrl).text();
            ocrApplied = true;
            logger.info("图片OCR识别完成，textLength={}", detectedText == null ? 0 : detectedText.length());
        }

        String finalAnswer;
        if (!plainAnswer.isBlank() && detectedText != null && !detectedText.isBlank()) {
            finalAnswer = "【学生文字作答】\n" + plainAnswer + "\n\n【图片识别文本】\n" + detectedText;
        } else if (!plainAnswer.isBlank()) {
            finalAnswer = plainAnswer;
        } else if (detectedText != null && !detectedText.isBlank()) {
            finalAnswer = "【图片识别文本】\n" + detectedText;
        } else {
            finalAnswer = "";
        }
        return new PreparedAnswer(finalAnswer, detectedText, ocrApplied);
    }

    private void validateImageDataUrl(String imageDataUrl) {
        if (!imageDataUrl.startsWith("data:image/") || !imageDataUrl.contains(";base64,")) {
            throw new RuntimeException("图片格式不正确，仅支持base64图片");
        }
    }

    private record PreparedAnswer(String finalAnswer, String detectedText, boolean ocrApplied) {
    }

}
