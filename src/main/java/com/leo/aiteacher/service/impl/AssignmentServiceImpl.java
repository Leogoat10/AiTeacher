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
            // 1. 验证课程是否存在且属于该教师
            Integer courseCount = courseMapper.countByCourseCodeAndTeacherId(courseCode, teacherId);
            if (courseCount == null || courseCount == 0) {
                result.put("success", false);
                result.put("message", "课程不存在或无权限");
                return result;
            }
            
            // 2. 验证题目内容不为空
            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "题目内容不能为空");
                return result;
            }
            
            // 3. 创建题目记录
            AssignmentDto assignment = new AssignmentDto();
            assignment.setMessageId(messageId); // messageId可以为null
            assignment.setTeacherId(teacherId);
            assignment.setCourseCode(courseCode);
            assignment.setTitle(title);
            assignment.setContent(content);
            
            assignmentMapper.insert(assignment);
            logger.info("创建题目记录成功，assignmentId={}", assignment.getId());
            
            // 4. 获取课程中的所有学生
            List<StuDto> students = studentMapper.listByCourseCode(courseCode);
            if (students == null || students.isEmpty()) {
                result.put("success", false);
                result.put("message", "课程中没有学生");
                return result;
            }
            
            // 5. 为每个学生创建题目接收记录
            int sentCount = 0;
            for (StuDto student : students) {
                StudentAssignmentDto studentAssignment = new StudentAssignmentDto();
                studentAssignment.setAssignmentId(assignment.getId());
                studentAssignment.setStudentId(student.getStudentId());
                studentAssignment.setIsRead(false);
                
                studentAssignmentMapper.insert(studentAssignment);
                sentCount++;
            }
            
            logger.info("题目发送成功，发送给{}个学生", sentCount);
            
            result.put("success", true);
            result.put("message", "题目发送成功");
            result.put("assignmentId", assignment.getId());
            result.put("sentCount", sentCount);
            
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
}
