package com.leo.aiteacher.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.pojo.dto.AssignmentDto;
import com.leo.aiteacher.pojo.dto.StudentAnswerDto;
import com.leo.aiteacher.pojo.mapper.AssignmentMapper;
import com.leo.aiteacher.pojo.mapper.StudentAnswerMapper;
import com.leo.aiteacher.service.StudentAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAnswerServiceImpl implements StudentAnswerService {
    
    private static final Logger logger = LoggerFactory.getLogger(StudentAnswerServiceImpl.class);
    
    @Value("${deepseek.api.url}")
    private String DEEPSEEK_API_URL;
    
    @Value("${deepseek.api.key}")
    private String API_KEY;
    
    @Autowired
    private StudentAnswerMapper studentAnswerMapper;
    
    @Autowired
    private AssignmentMapper assignmentMapper;
    
    @Override
    public Map<String, Object> submitAnswer(Integer assignmentId, Integer studentId, String studentAnswer) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 验证题目是否存在
            AssignmentDto assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                result.put("success", false);
                result.put("message", "题目不存在");
                return result;
            }
            
            // 2. 检查学生是否已经提交过答案
            StudentAnswerDto existingAnswer = studentAnswerMapper.getByAssignmentAndStudent(assignmentId, studentId);
            if (existingAnswer != null) {
                result.put("success", false);
                result.put("message", "已经提交过答案，不能重复提交");
                return result;
            }
            
            // 3. 构造AI评分和分析的提示词
            String promptMessage = constructPromptForEvaluation(assignment, studentAnswer);
            
            // 4. 调用DeepSeek API获取评分和分析
            String aiResponse = callDeepSeekAPI(promptMessage);
            
            // 5. 解析AI响应，提取评分和分析
            Map<String, String> evaluation = parseEvaluation(aiResponse);
            
            // 6. 保存学生答案和AI评分结果
            StudentAnswerDto studentAnswerDto = new StudentAnswerDto();
            studentAnswerDto.setAssignmentId(assignmentId);
            studentAnswerDto.setStudentId(studentId);
            studentAnswerDto.setStudentAnswer(studentAnswer);
            studentAnswerDto.setAiScore(evaluation.get("score"));
            studentAnswerDto.setAiAnalysis(evaluation.get("analysis"));
            
            studentAnswerMapper.insert(studentAnswerDto);
            
            logger.info("学生答案提交成功，assignmentId={}, studentId={}", assignmentId, studentId);
            
            result.put("success", true);
            result.put("message", "答案提交成功");
            result.put("score", evaluation.get("score"));
            result.put("analysis", evaluation.get("analysis"));
            
        } catch (Exception e) {
            logger.error("提交答案失败", e);
            result.put("success", false);
            result.put("message", "提交答案失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 构造用于AI评分的提示词
     */
    private String constructPromptForEvaluation(AssignmentDto assignment, String studentAnswer) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的教师，需要对学生的答题进行评分和分析，评分是可酌情给分，主要面向水平中等偏上学生。\n\n");
        prompt.append("题目标题：").append(assignment.getTitle()).append("\n\n");
        prompt.append("题目内容及标准答案：\n").append(assignment.getContent()).append("\n\n");
        prompt.append("学生的答案：\n").append(studentAnswer).append("\n\n");
        prompt.append("请按照以下格式输出评分和分析：\n");
        prompt.append("评分：（给出具体分数或等级）\n");
        prompt.append("分析：（详细分析学生的答题情况，指出优点和不足，给出改进建议）");
        
        return prompt.toString();
    }
    
    /**
     * 调用DeepSeek API
     */
    private String callDeepSeekAPI(String message) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + API_KEY);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", List.of(Map.of(
                "role", "user",
                "content", message
        )));
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        
        logger.info("调用DeepSeek API进行评分和分析");
        
        ResponseEntity<String> response = restTemplate.exchange(
                DEEPSEEK_API_URL,
                HttpMethod.POST,
                entity,
                String.class
        );
        
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response.getBody());
        JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
        
        return contentNode.isMissingNode() || contentNode.isNull() ? "" : contentNode.asText("");
    }
    
    /**
     * 解析AI返回的评分和分析
     */
    private Map<String, String> parseEvaluation(String aiResponse) {
        Map<String, String> result = new HashMap<>();
        
        // 尝试解析"评分："和"分析："格式
        String score = "";
        String analysis = "";
        
        String[] lines = aiResponse.split("\n");
        boolean isAnalysisSection = false;
        StringBuilder analysisBuilder = new StringBuilder();
        
        for (String line : lines) {
            if (line.startsWith("评分：") || line.startsWith("评分:")) {
                score = line.substring(3).trim();
            } else if (line.startsWith("分析：") || line.startsWith("分析:")) {
                isAnalysisSection = true;
                String analysisContent = line.substring(3).trim();
                if (!analysisContent.isEmpty()) {
                    analysisBuilder.append(analysisContent);
                }
            } else if (isAnalysisSection) {
                if (!line.trim().isEmpty()) {
                    if (analysisBuilder.length() > 0) {
                        analysisBuilder.append("\n");
                    }
                    analysisBuilder.append(line.trim());
                }
            }
        }
        
        analysis = analysisBuilder.toString();
        
        // 如果没有解析到评分或分析，使用原始响应
        if (score.isEmpty() && analysis.isEmpty()) {
            analysis = aiResponse;
            score = "待评";
        }
        
        result.put("score", score);
        result.put("analysis", analysis);
        
        return result;
    }
}
