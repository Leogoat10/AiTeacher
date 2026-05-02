package com.leo.aiteacher.pojo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leo.aiteacher.pojo.dto.AssignmentAnalysisSnapshotDto;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssignmentAnalysisSnapshotMapper extends BaseMapper<AssignmentAnalysisSnapshotDto> {

    @Insert("INSERT INTO assignment_analysis_snapshots " +
            "(teacher_id, course_code, assignment_id, assignment_title, overview_json, distribution_json, trend_json, weak_points_json, student_profiles_json, ai_recommendation_json, summary) " +
            "VALUES (#{teacherId}, #{courseCode}, #{assignmentId}, #{assignmentTitle}, #{overviewJson}, #{distributionJson}, #{trendJson}, #{weakPointsJson}, #{studentProfilesJson}, #{aiRecommendationJson}, #{summary}) " +
            "ON DUPLICATE KEY UPDATE " +
            "assignment_title = VALUES(assignment_title), " +
            "overview_json = VALUES(overview_json), " +
            "distribution_json = VALUES(distribution_json), " +
            "trend_json = VALUES(trend_json), " +
            "weak_points_json = VALUES(weak_points_json), " +
            "student_profiles_json = VALUES(student_profiles_json), " +
            "ai_recommendation_json = VALUES(ai_recommendation_json), " +
            "summary = VALUES(summary), " +
            "updated_at = CURRENT_TIMESTAMP")
    int upsert(AssignmentAnalysisSnapshotDto dto);

    @Select("SELECT * FROM assignment_analysis_snapshots " +
            "WHERE teacher_id = #{teacherId} AND course_code = #{courseCode} AND assignment_id = #{assignmentId} " +
            "LIMIT 1")
    AssignmentAnalysisSnapshotDto getByCourseAndAssignment(@Param("teacherId") Integer teacherId,
                                                           @Param("courseCode") String courseCode,
                                                           @Param("assignmentId") Integer assignmentId);
}
