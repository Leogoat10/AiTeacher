package com.leo.aiteacher.service.impl;

import com.leo.aiteacher.pojo.dto.AssignmentDto;
import com.leo.aiteacher.pojo.dto.StuDto;
import com.leo.aiteacher.pojo.dto.StudentAssignmentDto;
import com.leo.aiteacher.pojo.mapper.*;
import com.leo.aiteacher.service.AssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssignmentServiceImpl implements AssignmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(AssignmentServiceImpl.class);
    
    @Autowired
    private AssignmentMapper assignmentMapper;
    
    @Autowired
    private StudentAssignmentMapper studentAssignmentMapper;

    
    @Autowired
    private StudentMapper studentMapper;
    
    @Autowired
    private CourseMapper courseMapper;
    
    @Override
    @Transactional
    public Map<String, Object> sendAssignmentToCourse(Integer messageId, String content, String courseCode, Integer teacherId, String title) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (!hasCoursePermission(courseCode, teacherId)) {
                result.put("success", false);
                result.put("message", "课程不存在或无权限");
                return result;
            }
            
            if (title == null || title.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "题目标题不能为空");
                return result;
            }

            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "题目内容不能为空");
                return result;
            }

            List<StuDto> students = studentMapper.listByCourseCode(courseCode);
            if (students == null || students.isEmpty()) {
                result.put("success", false);
                result.put("message", "课程中没有学生");
                return result;
            }

            String singleBatchId = "single-" + UUID.randomUUID();
            AssignmentDto assignment = createAssignment(messageId, content, courseCode, teacherId, title, singleBatchId);
            int sentCount = bindAssignmentToStudents(assignment.getId(), students);

            logger.info("题目发送成功，assignmentId={}, sendBatchId={}, sentCount={}", assignment.getId(), singleBatchId, sentCount);
            
            result.put("success", true);
            result.put("message", "题目发送成功");
            result.put("assignmentId", assignment.getId());
            result.put("sentCount", sentCount);
            result.put("sendBatchId", singleBatchId);
            
        } catch (Exception e) {
            logger.error("发送题目失败", e);
            result.put("success", false);
            result.put("message", "发送题目失败: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getStudentAssignments(Integer studentId) {
        try {
            return studentAssignmentMapper.getStudentAssignmentsWithDetails(studentId);
        } catch (Exception e) {
            logger.error("获取学生题目列表失败", e);
            return null;
        }
    }
    
    @Override
    public List<Map<String, Object>> getCourseAssignments(String courseCode) {
        try {
            List<AssignmentDto> assignments = assignmentMapper.getByCourseCode(courseCode);
            // 转换为Map列表，方便前端使用
            return assignments.stream()
                    .map(a -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", a.getId());
                        map.put("title", a.getTitle());
                        map.put("content", a.getContent());
                        map.put("createdAt", a.getCreatedAt());
                        return map;
                    })
                    .toList();
        } catch (Exception e) {
            logger.error("获取课程题目列表失败", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> sendAssignmentsBatchToCourse(List<Map<String, Object>> assignments, String courseCode, Integer teacherId, String sendBatchId) {
        Map<String, Object> result = new HashMap<>();

        if (assignments == null || assignments.isEmpty()) {
            result.put("success", false);
            result.put("message", "题目列表不能为空");
            return result;
        }

        if (!hasCoursePermission(courseCode, teacherId)) {
            result.put("success", false);
            result.put("message", "课程不存在或无权限");
            return result;
        }

        List<StuDto> students = studentMapper.listByCourseCode(courseCode);
        if (students == null || students.isEmpty()) {
            result.put("success", false);
            result.put("message", "课程中没有学生");
            return result;
        }

        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> itemResults = new java.util.ArrayList<>();

        for (int i = 0; i < assignments.size(); i++) {
            Map<String, Object> item = assignments.get(i);
            Map<String, Object> itemResult = new HashMap<>();
            itemResult.put("index", i);

            try {
                Integer messageId = parseInteger(item.get("messageId"));
                String title = toSafeString(item.get("title"));
                String content = toSafeString(item.get("content"));

                if (title == null || title.trim().isEmpty()) {
                    throw new IllegalArgumentException("题目标题不能为空");
                }
                if (content == null || content.trim().isEmpty()) {
                    throw new IllegalArgumentException("题目内容不能为空");
                }

                AssignmentDto assignment = createAssignment(messageId, content, courseCode, teacherId, title, sendBatchId);
                int sentCount = bindAssignmentToStudents(assignment.getId(), students);

                successCount++;
                itemResult.put("success", true);
                itemResult.put("message", "发送成功");
                itemResult.put("assignmentId", assignment.getId());
                itemResult.put("title", title);
                itemResult.put("sentCount", sentCount);
            } catch (Exception e) {
                failCount++;
                itemResult.put("success", false);
                itemResult.put("message", e.getMessage());
                itemResult.put("title", toSafeString(item.get("title")));
                logger.error("批量发送题目失败，index={}, sendBatchId={}", i, sendBatchId, e);
            }

            itemResults.add(itemResult);
        }

        result.put("success", successCount > 0);
        result.put("message", failCount == 0 ? "批量发送成功" : "批量发送完成，部分失败");
        result.put("sendBatchId", sendBatchId);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("totalCount", assignments.size());
        result.put("studentCount", students.size());
        result.put("results", itemResults);
        return result;
    }

    private boolean hasCoursePermission(String courseCode, Integer teacherId) {
        Integer courseCount = courseMapper.countByCourseCodeAndTeacherId(courseCode, teacherId);
        return courseCount != null && courseCount > 0;
    }

    private AssignmentDto createAssignment(Integer messageId, String content, String courseCode, Integer teacherId, String title, String sendBatchId) {
        AssignmentDto assignment = new AssignmentDto();
        assignment.setMessageId(messageId);
        assignment.setTeacherId(teacherId);
        assignment.setCourseCode(courseCode);
        assignment.setTitle(title);
        assignment.setContent(content);
        assignment.setSendBatchId(sendBatchId);
        assignmentMapper.insert(assignment);
        return assignment;
    }

    private int bindAssignmentToStudents(Integer assignmentId, List<StuDto> students) {
        int sentCount = 0;
        for (StuDto student : students) {
            StudentAssignmentDto studentAssignment = new StudentAssignmentDto();
            studentAssignment.setAssignmentId(assignmentId);
            studentAssignment.setStudentId(student.getStudentId());
            studentAssignment.setIsRead(false);
            studentAssignmentMapper.insert(studentAssignment);
            sentCount++;
        }
        return sentCount;
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer intVal) return intVal;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String toSafeString(Object value) {
        return value == null ? null : value.toString();
    }
}
