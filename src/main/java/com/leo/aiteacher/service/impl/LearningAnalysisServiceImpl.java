package com.leo.aiteacher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.aiteacher.client.DeepSeekChatClient;
import com.leo.aiteacher.pojo.dto.AssignmentAnalysisSnapshotDto;
import com.leo.aiteacher.pojo.dto.AssignmentDto;
import com.leo.aiteacher.pojo.dto.LearningAnalysisLogDto;
import com.leo.aiteacher.pojo.dto.StudentAssignmentAnalysisDto;
import com.leo.aiteacher.pojo.mapper.AssignmentAnalysisSnapshotMapper;
import com.leo.aiteacher.pojo.mapper.AssignmentMapper;
import com.leo.aiteacher.pojo.mapper.CourseMapper;
import com.leo.aiteacher.pojo.mapper.LearningAnalysisLogMapper;
import com.leo.aiteacher.pojo.mapper.StudentAssignmentAnalysisMapper;
import com.leo.aiteacher.pojo.mapper.StudentAnswerMapper;
import com.leo.aiteacher.service.LearningAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LearningAnalysisServiceImpl implements LearningAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(LearningAnalysisServiceImpl.class);
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern ANALYSIS_KP_PATTERN = Pattern.compile("(?:知识点|薄弱点|易错点|薄弱环节)[:：]\\s*([^\\n；;。]{2,80})");
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)```json\\s*(\\{.*?\\})\\s*```");
    private static final Pattern RAW_JSON_PATTERN = Pattern.compile("(?s)(\\{.*\\})");
    private static final List<String> POSITIVE_HINTS = List.of("掌握", "准确", "清晰", "较好", "良好", "优秀", "完整", "正确");
    private static final List<String> NEGATIVE_HINTS = List.of("薄弱", "不熟悉", "错误", "遗漏", "不足", "混淆", "欠缺", "偏差");

    private final StudentAnswerMapper studentAnswerMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentAnalysisSnapshotMapper assignmentAnalysisSnapshotMapper;
    private final CourseMapper courseMapper;
    private final LearningAnalysisLogMapper learningAnalysisLogMapper;
    private final StudentAssignmentAnalysisMapper studentAssignmentAnalysisMapper;
    private final DeepSeekChatClient deepSeekChatClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private volatile boolean studentAssignmentTableReady = false;
    private volatile boolean assignmentSnapshotTableReady = false;

    public LearningAnalysisServiceImpl(StudentAnswerMapper studentAnswerMapper,
                                       AssignmentMapper assignmentMapper,
                                       AssignmentAnalysisSnapshotMapper assignmentAnalysisSnapshotMapper,
                                       CourseMapper courseMapper,
                                       LearningAnalysisLogMapper learningAnalysisLogMapper,
                                       StudentAssignmentAnalysisMapper studentAssignmentAnalysisMapper,
                                       DeepSeekChatClient deepSeekChatClient,
                                       ObjectMapper objectMapper,
                                       JdbcTemplate jdbcTemplate) {
        this.studentAnswerMapper = studentAnswerMapper;
        this.assignmentMapper = assignmentMapper;
        this.assignmentAnalysisSnapshotMapper = assignmentAnalysisSnapshotMapper;
        this.courseMapper = courseMapper;
        this.learningAnalysisLogMapper = learningAnalysisLogMapper;
        this.studentAssignmentAnalysisMapper = studentAssignmentAnalysisMapper;
        this.deepSeekChatClient = deepSeekChatClient;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getCourseLearningAnalysis(String courseCode, Integer teacherId, Integer assignmentId) {
        if (!hasCoursePermission(courseCode, teacherId)) {
            return fail("课程不存在或无权限");
        }

        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignmentId != null && assignment == null) {
            return fail("作业不存在或不属于当前课程");
        }

        List<Map<String, Object>> studentRows = assignmentId == null
                ? studentAnswerMapper.getCourseStudents(courseCode)
                : studentAnswerMapper.getCourseStudentsByAssignment(courseCode, assignmentId);
        List<Map<String, Object>> answerRows = assignmentId == null
                ? studentAnswerMapper.getCourseStudentAnswers(courseCode)
                : studentAnswerMapper.getCourseStudentAnswersByAssignment(courseCode, assignmentId);

        return buildAnalysisResult(courseCode, teacherId, assignmentId, assignment, studentRows, answerRows);
    }

    @Override
    public List<Map<String, Object>> listStudentsForAnalysis(String courseCode, Integer teacherId, Integer assignmentId) {
        if (!hasCoursePermission(courseCode, teacherId)) {
            return List.of();
        }
        if (assignmentId == null) {
            return studentAnswerMapper.getCourseStudents(courseCode);
        }
        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignment == null) {
            return List.of();
        }
        return studentAnswerMapper.getCourseStudentsByAssignment(courseCode, assignmentId);
    }

    @Override
    public Map<String, Object> runManualAnalysis(String courseCode, Integer teacherId, Integer assignmentId, List<Integer> studentIds) {
        ensureStudentAssignmentAnalysisTable();
        ensureAssignmentAnalysisSnapshotTable();
        if (!hasCoursePermission(courseCode, teacherId)) {
            return fail("课程不存在或无权限");
        }
        if (assignmentId == null) {
            return fail("请先选择作业");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            return fail("请至少选择一名学生");
        }

        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignment == null) {
            return fail("作业不存在或不属于当前课程");
        }

        Set<Integer> selectedSet = new HashSet<>(studentIds);
        List<Map<String, Object>> studentRows = studentAnswerMapper.getCourseStudentsByAssignment(courseCode, assignmentId)
                .stream()
                .filter(row -> selectedSet.contains(intValue(row, "studentId", "student_id")))
                .toList();
        List<Map<String, Object>> answerRows = studentAnswerMapper.getCourseStudentAnswersByAssignment(courseCode, assignmentId)
                .stream()
                .filter(row -> selectedSet.contains(intValue(row, "student_id", "studentId")))
                .toList();

        if (studentRows.isEmpty()) {
            return fail("所选学生不在该课程中");
        }
        Map<String, Object> result = buildAnalysisResult(courseCode, teacherId, assignmentId, assignment, studentRows, answerRows);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> profiles = (List<Map<String, Object>>) result.get("studentProfiles");
        persistStudentAssignmentAnalyses(teacherId, courseCode, assignmentId, profiles);
        persistAssignmentAnalysisSnapshot(teacherId, courseCode, assignmentId, result);
        result.put("studentProfiles", listSavedStudentAnalyses(courseCode, teacherId, assignmentId));
        return result;
    }

    @Override
    public List<Map<String, Object>> listSavedStudentAnalyses(String courseCode, Integer teacherId, Integer assignmentId) {
        ensureStudentAssignmentAnalysisTable();
        if (!hasCoursePermission(courseCode, teacherId) || assignmentId == null) {
            return List.of();
        }
        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignment == null) {
            return List.of();
        }
        List<StudentAssignmentAnalysisDto> saved = studentAssignmentAnalysisMapper.listByCourseAndAssignment(
                teacherId, courseCode, assignmentId
        );
        List<Map<String, Object>> data = new ArrayList<>();
        for (StudentAssignmentAnalysisDto item : saved) {
            data.add(savedToProfile(item));
        }
        return data;
    }

    @Override
    public Map<String, Object> getLatestSavedAnalysisResult(String courseCode, Integer teacherId, Integer assignmentId) {
        ensureStudentAssignmentAnalysisTable();
        ensureAssignmentAnalysisSnapshotTable();
        if (!hasCoursePermission(courseCode, teacherId)) {
            return fail("课程不存在或无权限");
        }
        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignment == null) {
            return fail("作业不存在或不属于当前课程");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("courseCode", courseCode);
        result.put("assignmentId", assignmentId);
        result.put("assignmentTitle", assignment.getTitle());

        AssignmentAnalysisSnapshotDto snapshot = assignmentAnalysisSnapshotMapper.getByCourseAndAssignment(
                teacherId, courseCode, assignmentId
        );
        if (snapshot == null) {
            result.put("studentProfiles", listSavedStudentAnalyses(courseCode, teacherId, assignmentId));
            result.put("hasData", false);
            return result;
        }

        result.put("hasData", true);
        result.put("generatedAt", snapshot.getUpdatedAt() == null ? snapshot.getCreatedAt() : snapshot.getUpdatedAt());
        result.put("overview", parseMapJson(snapshot.getOverviewJson()));
        result.put("distribution", parseMapJson(snapshot.getDistributionJson()));
        result.put("weakKnowledgePoints", parseListMapJson(snapshot.getWeakPointsJson()));
        result.put("trend", parseListMapJson(snapshot.getTrendJson()));
        result.put("studentProfiles", parseListMapJson(snapshot.getStudentProfilesJson()));
        result.put("aiRecommendation", parseMapJson(snapshot.getAiRecommendationJson()));
        result.put("summary", snapshot.getSummary());
        return result;
    }

    private Map<String, Object> buildAnalysisResult(String courseCode,
                                                    Integer teacherId,
                                                    Integer assignmentId,
                                                    AssignmentDto assignment,
                                                    List<Map<String, Object>> studentRows,
                                                    List<Map<String, Object>> answerRows) {
        Map<String, Object> result = new HashMap<>();
        Map<Integer, StudentAggregate> aggregateByStudent = buildStudentAggregates(studentRows);
        Map<LocalDate, TrendAggregate> trendMap = new TreeMap<>();
        Map<String, Integer> weakPointCounter = new HashMap<>();

        double totalScoreSum = 0;
        int totalScoreCount = 0;

        for (Map<String, Object> row : answerRows) {
            Integer studentId = intValue(row, "student_id", "studentId");
            if (studentId == null) {
                continue;
            }
            StudentAggregate aggregate = aggregateByStudent.computeIfAbsent(studentId, k -> new StudentAggregate());
            aggregate.studentId = studentId;
            aggregate.studentName = stringValue(row, "student_name", "studentName");
            aggregate.answerCount++;

            Double score = parseScore(stringValue(row, "ai_score", "aiScore"));
            if (score != null) {
                aggregate.scoreSum += score;
                aggregate.scoreCount++;
                totalScoreSum += score;
                totalScoreCount++;
            }

            String analysis = stringValue(row, "ai_analysis", "aiAnalysis");
            String evaluationJson = stringValue(row, "evaluation_json", "evaluationJson");
            double modelSignal = evaluateModelSignal(analysis, evaluationJson);
            aggregate.signalSum += modelSignal;
            aggregate.signalCount++;

            mergeWeakPointCounter(weakPointCounter, extractWeakPoints(analysis, evaluationJson));

            LocalDate submitDate = toLocalDate(row.get("submitted_at"));
            if (submitDate != null && score != null) {
                TrendAggregate trend = trendMap.computeIfAbsent(submitDate, k -> new TrendAggregate());
                trend.answerCount++;
                trend.scoreSum += score;
            }
        }

        int excellent = 0;
        int good = 0;
        int improve = 0;
        int weak = 0;
        int evaluatedStudents = 0;

        List<Map<String, Object>> studentProfiles = new ArrayList<>();
        for (StudentAggregate aggregate : aggregateByStudent.values()) {
            if (aggregate.studentId == null) {
                continue;
            }
            double avgScore = aggregate.scoreCount == 0 ? 0 : aggregate.scoreSum / aggregate.scoreCount;
            double avgSignal = aggregate.signalCount == 0 ? 60 : aggregate.signalSum / aggregate.signalCount;
            double preparednessScore = aggregate.answerCount == 0 ? 0 : weightedPreparedness(avgScore, avgSignal);
            String level = levelByScore(preparednessScore);
            String recommendation = recommendationByLevel(level);

            if (aggregate.answerCount > 0) {
                evaluatedStudents++;
                if ("优秀".equals(level)) excellent++;
                else if ("良好".equals(level)) good++;
                else if ("待提升".equals(level)) improve++;
                else weak++;
            }

            Map<String, Object> profile = new HashMap<>();
            profile.put("studentId", aggregate.studentId);
            profile.put("studentName", aggregate.studentName == null ? ("学生" + aggregate.studentId) : aggregate.studentName);
            profile.put("answerCount", aggregate.answerCount);
            profile.put("avgScore", round2(avgScore));
            profile.put("preparednessScore", round2(preparednessScore));
            profile.put("masteryLevel", level);
            profile.put("recommendation", recommendation);
            studentProfiles.add(profile);
        }

        studentProfiles.sort(Comparator.comparingDouble(o -> ((Number) o.get("preparednessScore")).doubleValue()));

        double avgScore = totalScoreCount == 0 ? 0 : totalScoreSum / totalScoreCount;
        double masteryRate = evaluatedStudents == 0 ? 0 : ((excellent + good) * 100.0 / evaluatedStudents);
        int totalStudents = aggregateByStudent.size();
        long coveredStudents = studentProfiles.stream()
                .filter(s -> ((Number) s.get("answerCount")).intValue() > 0)
                .count();
        double answerCoverage = totalStudents == 0 ? 0 : (coveredStudents * 100.0 / totalStudents);
        int riskStudentCount = improve + weak;

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalStudents", totalStudents);
        overview.put("totalAnswers", answerRows.size());
        overview.put("avgScore", round2(avgScore));
        overview.put("answerCoverage", round2(answerCoverage));
        overview.put("masteryRate", round2(masteryRate));
        overview.put("riskStudentCount", riskStudentCount);
        overview.put("masteryLevel", levelByScore(weightedPreparedness(avgScore, 60)));

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("excellent", excellent);
        distribution.put("good", good);
        distribution.put("improve", improve);
        distribution.put("weak", weak);
        distribution.put("evaluatedStudents", evaluatedStudents);

        List<Map<String, Object>> trendList = new ArrayList<>();
        for (Map.Entry<LocalDate, TrendAggregate> entry : trendMap.entrySet()) {
            TrendAggregate trend = entry.getValue();
            Map<String, Object> trendItem = new HashMap<>();
            trendItem.put("date", entry.getKey().toString());
            trendItem.put("answerCount", trend.answerCount);
            trendItem.put("avgScore", trend.answerCount == 0 ? 0 : round2(trend.scoreSum / trend.answerCount));
            trendList.add(trendItem);
        }

        List<Map<String, Object>> weakPoints = topWeakPoints(weakPointCounter, 8);
        String summary = buildSummary(totalStudents, evaluatedStudents, overview.get("masteryRate"), riskStudentCount, weakPoints);
        Map<String, Object> aiRecommendation = generateAiRecommendation(courseCode, assignment, overview, distribution, weakPoints, studentProfiles);

        persistAnalysisLog(teacherId, courseCode, assignmentId, overview, weakPoints, studentProfiles, summary);

        result.put("success", true);
        result.put("courseCode", courseCode);
        result.put("assignmentId", assignmentId);
        result.put("assignmentTitle", assignment == null ? null : assignment.getTitle());
        result.put("generatedAt", LocalDateTime.now());
        result.put("overview", overview);
        result.put("distribution", distribution);
        result.put("trend", trendList);
        result.put("weakKnowledgePoints", weakPoints);
        result.put("studentProfiles", studentProfiles);
        result.put("summary", summary);
        result.put("aiRecommendation", aiRecommendation);
        return result;
    }

    @Override
    public Map<String, Object> getStudentLearningProfile(Integer studentId, String courseCode, Integer teacherId, Integer assignmentId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasCoursePermission(courseCode, teacherId)) {
            result.put("success", false);
            result.put("message", "课程不存在或无权限");
            return result;
        }

        AssignmentDto assignment = resolveAssignment(courseCode, teacherId, assignmentId);
        if (assignmentId != null && assignment == null) {
            result.put("success", false);
            result.put("message", "作业不存在或不属于当前课程");
            return result;
        }

        if (assignmentId != null) {
            ensureStudentAssignmentAnalysisTable();
            StudentAssignmentAnalysisDto saved = studentAssignmentAnalysisMapper.getByAssignmentAndStudent(assignmentId, studentId);
            if (saved == null || !Objects.equals(saved.getTeacherId(), teacherId) || !Objects.equals(saved.getCourseCode(), courseCode)) {
                result.put("success", false);
                result.put("message", "该学生该次作业暂无学情分析，请先手动触发");
                return result;
            }
            List<Map<String, Object>> records = studentAnswerMapper.getStudentAnswerHistoryByAssignment(studentId, courseCode, assignmentId);
            result.put("success", true);
            result.put("courseCode", courseCode);
            result.put("assignmentId", assignmentId);
            result.put("assignmentTitle", assignment.getTitle());
            result.put("profile", savedToProfile(saved));
            result.put("records", records);
            return result;
        }

        List<Map<String, Object>> records = assignmentId == null
                ? studentAnswerMapper.getStudentAnswerHistory(studentId, courseCode)
                : studentAnswerMapper.getStudentAnswerHistoryByAssignment(studentId, courseCode, assignmentId);
        if (records == null || records.isEmpty()) {
            result.put("success", false);
            result.put("message", "该学生在当前课程暂无答题记录");
            return result;
        }

        double scoreSum = 0;
        int scoreCount = 0;
        double signalSum = 0;
        int signalCount = 0;
        Map<String, Integer> weakPointCounter = new HashMap<>();
        String studentName = null;

        for (Map<String, Object> row : records) {
            if (studentName == null) {
                studentName = stringValue(row, "studentName", "student_name");
            }

            Double score = parseScore(stringValue(row, "aiScore", "ai_score"));
            if (score != null) {
                scoreSum += score;
                scoreCount++;
            }

            String analysis = stringValue(row, "aiAnalysis", "ai_analysis");
            String evaluationJson = stringValue(row, "evaluationJson", "evaluation_json");
            signalSum += evaluateModelSignal(analysis, evaluationJson);
            signalCount++;
            mergeWeakPointCounter(weakPointCounter, extractWeakPoints(analysis, evaluationJson));
        }

        double avgScore = scoreCount == 0 ? 0 : scoreSum / scoreCount;
        double avgSignal = signalCount == 0 ? 60 : signalSum / signalCount;
        double preparednessScore = weightedPreparedness(avgScore, avgSignal);
        String masteryLevel = levelByScore(preparednessScore);

        Map<String, Object> profile = new HashMap<>();
        profile.put("studentId", studentId);
        profile.put("studentName", studentName == null ? ("学生" + studentId) : studentName);
        profile.put("answerCount", records.size());
        profile.put("avgScore", round2(avgScore));
        profile.put("preparednessScore", round2(preparednessScore));
        profile.put("masteryLevel", masteryLevel);
        profile.put("recommendation", recommendationByLevel(masteryLevel));
        profile.put("weakKnowledgePoints", topWeakPoints(weakPointCounter, 6));

        result.put("success", true);
        result.put("courseCode", courseCode);
        result.put("assignmentId", assignmentId);
        result.put("assignmentTitle", assignment == null ? null : assignment.getTitle());
        result.put("profile", profile);
        result.put("records", records);
        return result;
    }

    @Override
    public Map<String, Object> listAnalysisLogs(String courseCode, Integer teacherId, Integer limit, Integer assignmentId) {
        Map<String, Object> result = new HashMap<>();
        if (!hasCoursePermission(courseCode, teacherId)) {
            result.put("success", false);
            result.put("message", "课程不存在或无权限");
            return result;
        }

        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        QueryWrapper<LearningAnalysisLogDto> query = new QueryWrapper<LearningAnalysisLogDto>()
                .eq("teacher_id", teacherId)
                .eq("course_code", courseCode)
                .orderByDesc("created_at")
                .last("LIMIT " + safeLimit);
        if (assignmentId != null) {
            query.eq("assignment_id", assignmentId);
        }
        List<LearningAnalysisLogDto> logs = learningAnalysisLogMapper.selectList(query);

        List<Map<String, Object>> data = new ArrayList<>();
        for (LearningAnalysisLogDto log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("createdAt", log.getCreatedAt());
            item.put("assignmentId", log.getAssignmentId());
            item.put("totalStudents", log.getTotalStudents());
            item.put("totalAnswers", log.getTotalAnswers());
            item.put("avgScore", log.getAvgScore());
            item.put("masteryLevel", log.getMasteryLevel());
            item.put("masteryRate", log.getMasteryRate());
            item.put("riskStudentCount", log.getRiskStudentCount());
            item.put("analysisSummary", log.getAnalysisSummary());
            data.add(item);
        }

        result.put("success", true);
        result.put("courseCode", courseCode);
        result.put("logs", data);
        return result;
    }

    private Map<Integer, StudentAggregate> buildStudentAggregates(List<Map<String, Object>> studentRows) {
        Map<Integer, StudentAggregate> map = new HashMap<>();
        for (Map<String, Object> row : studentRows) {
            Integer studentId = intValue(row, "studentId", "student_id");
            if (studentId == null) {
                continue;
            }
            StudentAggregate aggregate = new StudentAggregate();
            aggregate.studentId = studentId;
            aggregate.studentName = stringValue(row, "studentName", "student_name");
            map.put(studentId, aggregate);
        }
        return map;
    }

    private boolean hasCoursePermission(String courseCode, Integer teacherId) {
        Integer owned = courseMapper.countByCourseCodeAndTeacherId(courseCode, teacherId);
        return owned != null && owned > 0;
    }

    private AssignmentDto resolveAssignment(String courseCode, Integer teacherId, Integer assignmentId) {
        if (assignmentId == null) {
            return null;
        }
        AssignmentDto assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            return null;
        }
        if (!Objects.equals(assignment.getTeacherId(), teacherId)) {
            return null;
        }
        if (!Objects.equals(assignment.getCourseCode(), courseCode)) {
            return null;
        }
        return assignment;
    }

    private Double parseScore(String rawScore) {
        if (rawScore == null || rawScore.isBlank()) {
            return null;
        }
        Matcher matcher = SCORE_PATTERN.matcher(rawScore);
        if (!matcher.find()) {
            return null;
        }
        try {
            double score = Double.parseDouble(matcher.group(1));
            return Math.max(0, Math.min(100, score));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double evaluateModelSignal(String analysis, String evaluationJson) {
        double signalFromJson = scoreFromEvaluationJson(evaluationJson);
        double signal = signalFromJson >= 0 ? signalFromJson : 60;
        if (analysis == null || analysis.isBlank()) {
            return clamp(signal);
        }
        for (String hint : POSITIVE_HINTS) {
            if (analysis.contains(hint)) {
                signal += 4;
            }
        }
        for (String hint : NEGATIVE_HINTS) {
            if (analysis.contains(hint)) {
                signal -= 6;
            }
        }
        return clamp(signal);
    }

    private double scoreFromEvaluationJson(String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) {
            return -1;
        }
        try {
            JsonNode root = objectMapper.readTree(evaluationJson);
            if (root.has("totalScore") && root.has("maxScore")) {
                double total = root.path("totalScore").asDouble(-1);
                double max = root.path("maxScore").asDouble(-1);
                if (total >= 0 && max > 0) {
                    return clamp(total * 100.0 / max);
                }
            }
            JsonNode itemScores = root.path("itemScores");
            if (itemScores.isArray() && !itemScores.isEmpty()) {
                double ratioSum = 0;
                int count = 0;
                for (JsonNode item : itemScores) {
                    double score = item.path("score").asDouble(Double.NaN);
                    double full = item.path("fullScore").asDouble(Double.NaN);
                    if (Double.isNaN(full) || full <= 0) {
                        full = item.path("maxScore").asDouble(Double.NaN);
                    }
                    if (Double.isNaN(score) || Double.isNaN(full) || full <= 0) {
                        continue;
                    }
                    ratioSum += (score / full);
                    count++;
                }
                if (count > 0) {
                    return clamp(ratioSum * 100.0 / count);
                }
            }
            return -1;
        } catch (Exception e) {
            logger.debug("解析 evaluation_json 失败: {}", e.getMessage());
            return -1;
        }
    }

    private List<String> extractWeakPoints(String analysis, String evaluationJson) {
        Set<String> points = new LinkedHashSet<>();
        extractWeakPointsFromJson(points, evaluationJson);
        extractWeakPointsFromText(points, analysis);
        return new ArrayList<>(points);
    }

    private void extractWeakPointsFromJson(Set<String> points, String evaluationJson) {
        if (evaluationJson == null || evaluationJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(evaluationJson);
            JsonNode itemScores = root.path("itemScores");
            if (!itemScores.isArray()) {
                return;
            }
            for (JsonNode item : itemScores) {
                String kp = item.path("knowledgePoint").asText(null);
                double score = item.path("score").asDouble(Double.NaN);
                double full = item.path("fullScore").asDouble(Double.NaN);
                if (Double.isNaN(full) || full <= 0) {
                    full = item.path("maxScore").asDouble(Double.NaN);
                }
                if (kp == null || kp.isBlank() || Double.isNaN(score) || Double.isNaN(full) || full <= 0) {
                    continue;
                }
                if (score / full < 0.6) {
                    points.add(normalizeKnowledgePoint(kp));
                }
            }
        } catch (Exception e) {
            logger.debug("从 evaluation_json 提取知识点失败: {}", e.getMessage());
        }
    }

    private void extractWeakPointsFromText(Set<String> points, String analysis) {
        if (analysis == null || analysis.isBlank()) {
            return;
        }
        Matcher matcher = ANALYSIS_KP_PATTERN.matcher(analysis);
        while (matcher.find()) {
            String segment = matcher.group(1);
            for (String item : segment.split("[、,，/\\s]+")) {
                String normalized = normalizeKnowledgePoint(item);
                if (normalized.length() >= 2 && normalized.length() <= 30) {
                    points.add(normalized);
                }
            }
        }
    }

    private String normalizeKnowledgePoint(String source) {
        if (source == null) {
            return "";
        }
        return source.trim()
                .replaceAll("[。；;：:,.，\\-]+$", "")
                .replaceAll("^[-:：,，\\s]+", "");
    }

    private void mergeWeakPointCounter(Map<String, Integer> weakPointCounter, List<String> weakPoints) {
        for (String point : weakPoints) {
            if (point == null || point.isBlank()) {
                continue;
            }
            weakPointCounter.merge(point, 1, Integer::sum);
        }
    }

    private List<Map<String, Object>> topWeakPoints(Map<String, Integer> weakPointCounter, int maxSize) {
        List<Map<String, Object>> data = new ArrayList<>();
        weakPointCounter.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(maxSize)
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("knowledgePoint", entry.getKey());
                    item.put("frequency", entry.getValue());
                    data.add(item);
                });
        return data;
    }

    private String buildSummary(int totalStudents, int evaluatedStudents, Object masteryRate,
                                int riskStudentCount, List<Map<String, Object>> weakPoints) {
        String firstWeak = weakPoints.isEmpty() ? "暂无明显集中薄弱点" : String.valueOf(weakPoints.get(0).get("knowledgePoint"));
        return String.format("共纳入%d名学生，完成有效分析%d人，达标率%s%%，重点关注%d人。当前最突出薄弱知识点：%s。",
                totalStudents, evaluatedStudents, masteryRate, riskStudentCount, firstWeak);
    }

    private Map<String, Object> generateAiRecommendation(String courseCode,
                                                         AssignmentDto assignment,
                                                         Map<String, Object> overview,
                                                         Map<String, Object> distribution,
                                                         List<Map<String, Object>> weakPoints,
                                                         List<Map<String, Object>> studentProfiles) {
        Map<String, Object> fallback = fallbackRecommendation(overview, weakPoints);
        try {
            String weakestStudents = studentProfiles.stream()
                    .limit(Math.min(5, studentProfiles.size()))
                    .map(s -> s.get("studentName") + "(" + s.get("masteryLevel") + ", " + s.get("preparednessScore") + ")")
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("暂无");
            String weakPointText = weakPoints.stream()
                    .map(p -> p.get("knowledgePoint") + "(" + p.get("frequency") + ")")
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("暂无");
            String scope = assignment == null
                    ? "课程整体学情"
                    : "单次作业学情（作业ID=" + assignment.getId() + "，标题=" + assignment.getTitle() + "）";

            String prompt = """
                    你是一名教学数据分析专家，请基于以下学情统计输出可执行建议。
                    输出必须是 JSON 对象，不要包含 Markdown 或额外解释。
                    JSON结构:
                    {
                      "overallInsight": "50字内总体结论",
                      "teacherSuggestions": ["建议1","建议2","建议3"],
                      "studentSuggestions": ["建议1","建议2","建议3"],
                      "resourceSuggestions": ["资源建议1","资源建议2"]
                    }
                    要求：
                    1) 建议必须和数据强关联，避免空泛表述；
                    2) 至少给出3条教师建议、3条学生建议；
                    3) 重点围绕薄弱知识点与风险学生展开。
                    
                    分析范围：%s
                    课程代码：%s
                    核心指标：%s
                    分层统计：%s
                    薄弱知识点：%s
                    风险学生样本：%s
                    """.formatted(
                    scope,
                    courseCode,
                    objectMapper.writeValueAsString(overview),
                    objectMapper.writeValueAsString(distribution),
                    weakPointText,
                    weakestStudents
            );

            DeepSeekChatClient.ChatResult chatResult = deepSeekChatClient.chat(prompt);
            String jsonText = extractJson(chatResult.content());
            if (jsonText == null || jsonText.isBlank()) {
                jsonText = extractJson(chatResult.rawResponse());
            }
            if (jsonText == null || jsonText.isBlank()) {
                return fallback;
            }

            JsonNode root = objectMapper.readTree(jsonText);
            if (!root.isObject()) {
                return fallback;
            }

            Map<String, Object> ai = new HashMap<>();
            ai.put("overallInsight", root.path("overallInsight").asText("暂无AI结论"));
            ai.put("teacherSuggestions", jsonTextArray(root.path("teacherSuggestions")));
            ai.put("studentSuggestions", jsonTextArray(root.path("studentSuggestions")));
            ai.put("resourceSuggestions", jsonTextArray(root.path("resourceSuggestions")));
            ai.put("modelName", chatResult.modelName());
            return ai;
        } catch (Exception e) {
            logger.warn("生成AI学情建议失败，courseCode={}, assignmentId={}, reason={}",
                    courseCode, assignment == null ? null : assignment.getId(), e.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> fallbackRecommendation(Map<String, Object> overview,
                                                       List<Map<String, Object>> weakPoints) {
        String weakPoint = weakPoints.isEmpty() ? "基础知识点" : String.valueOf(weakPoints.get(0).get("knowledgePoint"));
        List<String> teacherSuggestions = new ArrayList<>();
        teacherSuggestions.add("围绕“" + weakPoint + "”安排10-15分钟小测并当堂讲评。");
        teacherSuggestions.add("对待提升与薄弱学生分层布置作业，减少重复性机械训练。");
        teacherSuggestions.add("下一次课前先做预备知识回顾，再进入新授内容。");

        List<String> studentSuggestions = new ArrayList<>();
        studentSuggestions.add("先完成错题复盘，标记失分原因并重做同类题。");
        studentSuggestions.add("每天固定15分钟专项练习“" + weakPoint + "”。");
        studentSuggestions.add("将不会的题整理成问题清单，课后向老师集中提问。");

        List<String> resourceSuggestions = new ArrayList<>();
        resourceSuggestions.add("优先分发“" + weakPoint + "”微课视频与基础题单。");
        resourceSuggestions.add("提供一套分层练习（基础/提升）并设置完成反馈。");

        Map<String, Object> ai = new HashMap<>();
        ai.put("overallInsight", "当前达标率" + overview.get("masteryRate") + "%，应优先解决核心薄弱点并关注风险学生。");
        ai.put("teacherSuggestions", teacherSuggestions);
        ai.put("studentSuggestions", studentSuggestions);
        ai.put("resourceSuggestions", resourceSuggestions);
        ai.put("modelName", "rule-fallback");
        return ai;
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            return blockMatcher.group(1);
        }
        Matcher rawMatcher = RAW_JSON_PATTERN.matcher(text.trim());
        if (rawMatcher.find()) {
            return rawMatcher.group(1);
        }
        return null;
    }

    private List<String> jsonTextArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private void persistAnalysisLog(Integer teacherId, String courseCode, Integer assignmentId, Map<String, Object> overview,
                                    List<Map<String, Object>> weakPoints, List<Map<String, Object>> studentProfiles,
                                    String summary) {
        try {
            LearningAnalysisLogDto log = new LearningAnalysisLogDto();
            log.setTeacherId(teacherId);
            log.setCourseCode(courseCode);
            log.setAssignmentId(assignmentId);
            log.setTotalStudents((Integer) overview.get("totalStudents"));
            log.setTotalAnswers((Integer) overview.get("totalAnswers"));
            log.setAvgScore(toDouble(overview.get("avgScore")));
            log.setMasteryLevel((String) overview.get("masteryLevel"));
            log.setMasteryRate(toDouble(overview.get("masteryRate")));
            log.setRiskStudentCount((Integer) overview.get("riskStudentCount"));
            log.setKnowledgePointsJson(objectMapper.writeValueAsString(weakPoints));
            log.setStudentSnapshotJson(objectMapper.writeValueAsString(studentProfiles));
            log.setAnalysisSummary(summary);
            learningAnalysisLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("保存学情分析日志失败，courseCode={}, teacherId={}, reason={}",
                    courseCode, teacherId, e.getMessage());
        }
    }

    private void persistAssignmentAnalysisSnapshot(Integer teacherId,
                                                   String courseCode,
                                                   Integer assignmentId,
                                                   Map<String, Object> result) {
        if (assignmentId == null || result == null) {
            return;
        }
        try {
            AssignmentAnalysisSnapshotDto dto = new AssignmentAnalysisSnapshotDto();
            dto.setTeacherId(teacherId);
            dto.setCourseCode(courseCode);
            dto.setAssignmentId(assignmentId);
            dto.setAssignmentTitle(stringValue(result, "assignmentTitle"));
            dto.setOverviewJson(objectMapper.writeValueAsString(result.get("overview")));
            dto.setDistributionJson(objectMapper.writeValueAsString(result.get("distribution")));
            dto.setTrendJson(objectMapper.writeValueAsString(result.get("trend")));
            dto.setWeakPointsJson(objectMapper.writeValueAsString(result.get("weakKnowledgePoints")));
            dto.setStudentProfilesJson(objectMapper.writeValueAsString(result.get("studentProfiles")));
            dto.setAiRecommendationJson(objectMapper.writeValueAsString(result.get("aiRecommendation")));
            dto.setSummary(stringValue(result, "summary"));
            assignmentAnalysisSnapshotMapper.upsert(dto);
        } catch (Exception e) {
            logger.warn("保存作业学情快照失败，courseCode={}, assignmentId={}, teacherId={}, reason={}",
                    courseCode, assignmentId, teacherId, e.getMessage());
        }
    }

    private void persistStudentAssignmentAnalyses(Integer teacherId,
                                                  String courseCode,
                                                  Integer assignmentId,
                                                  List<Map<String, Object>> studentProfiles) {
        if (assignmentId == null || studentProfiles == null || studentProfiles.isEmpty()) {
            return;
        }
        AssignmentDto assignment = assignmentMapper.selectById(assignmentId);
        for (Map<String, Object> profile : studentProfiles) {
            Integer studentId = intValue(profile, "studentId");
            if (studentId == null) {
                continue;
            }
            try {
                String aiAnalysis = generateStudentAiAnalysis(courseCode, assignment, profile);
                profile.put("aiAnalysis", aiAnalysis);
                StudentAssignmentAnalysisDto dto = new StudentAssignmentAnalysisDto();
                dto.setTeacherId(teacherId);
                dto.setCourseCode(courseCode);
                dto.setAssignmentId(assignmentId);
                dto.setStudentId(studentId);
                dto.setAnswerCount(intValue(profile, "answerCount"));
                dto.setAvgScore(toDouble(profile.get("avgScore")));
                dto.setPreparednessScore(toDouble(profile.get("preparednessScore")));
                dto.setMasteryLevel(stringValue(profile, "masteryLevel"));
                dto.setRecommendation(stringValue(profile, "recommendation"));
                dto.setAnalysisJson(objectMapper.writeValueAsString(profile));
                studentAssignmentAnalysisMapper.upsert(dto);
            } catch (Exception e) {
                logger.warn("保存学生作业学情失败，assignmentId={}, studentId={}, reason={}",
                        assignmentId, studentId, e.getMessage());
            }
        }
    }

    private String generateStudentAiAnalysis(String courseCode, AssignmentDto assignment, Map<String, Object> profile) {
        try {
            String assignmentText = assignment == null
                    ? "未知作业"
                    : ("作业ID=" + assignment.getId() + "，标题=" + assignment.getTitle());
            String prompt = """
                    你是一名资深班主任，请基于学生画像生成该学生本次作业的个性化学情分析。
                    输出必须是纯文本，不要JSON，不要markdown，不超过180字。
                    需要包含：学习现状判断、主要问题、下一步建议。
                    
                    课程：%s
                    作业：%s
                    学生画像：%s
                    """.formatted(
                    courseCode,
                    assignmentText,
                    objectMapper.writeValueAsString(profile)
            );
            DeepSeekChatClient.ChatResult chatResult = deepSeekChatClient.chat(prompt);
            String content = chatResult.content() == null ? "" : chatResult.content().trim();
            if (!content.isEmpty()) {
                return content;
            }
        } catch (Exception e) {
            logger.warn("生成学生AI分析失败，studentId={}, reason={}", profile.get("studentId"), e.getMessage());
        }
        return "该生当前掌握度为" + stringValue(profile, "masteryLevel") + "，建议按薄弱点进行针对性巩固并跟踪改进。";
    }

    private Map<String, Object> savedToProfile(StudentAssignmentAnalysisDto saved) {
        Map<String, Object> profile = new HashMap<>();
        try {
            if (saved.getAnalysisJson() != null && !saved.getAnalysisJson().isBlank()) {
                Map<?, ?> raw = objectMapper.readValue(saved.getAnalysisJson(), Map.class);
                raw.forEach((k, v) -> profile.put(String.valueOf(k), v));
            }
        } catch (Exception ignored) {
        }
        profile.put("studentId", saved.getStudentId());
        if (!profile.containsKey("studentName")) {
            profile.put("studentName", "学生" + saved.getStudentId());
        }
        profile.put("answerCount", saved.getAnswerCount());
        profile.put("avgScore", round2(saved.getAvgScore() == null ? 0 : saved.getAvgScore()));
        profile.put("preparednessScore", round2(saved.getPreparednessScore() == null ? 0 : saved.getPreparednessScore()));
        profile.put("masteryLevel", saved.getMasteryLevel());
        profile.put("recommendation", saved.getRecommendation());
        return profile;
    }

    private Map<String, Object> buildDistributionFromProfiles(List<Map<String, Object>> profiles) {
        int excellent = 0, good = 0, improve = 0, weak = 0;
        for (Map<String, Object> profile : profiles) {
            String level = stringValue(profile, "masteryLevel");
            if ("优秀".equals(level)) excellent++;
            else if ("良好".equals(level)) good++;
            else if ("待提升".equals(level)) improve++;
            else if ("薄弱".equals(level)) weak++;
        }
        Map<String, Object> distribution = new HashMap<>();
        distribution.put("excellent", excellent);
        distribution.put("good", good);
        distribution.put("improve", improve);
        distribution.put("weak", weak);
        return distribution;
    }

    private List<Map<String, Object>> parseListMapJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMapJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Integer intValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String stringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private LocalDate toLocalDate(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (source instanceof Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        try {
            return LocalDateTime.parse(source.toString()).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private double weightedPreparedness(double avgScore, double modelSignal) {
        return clamp(avgScore * 0.7 + modelSignal * 0.3);
    }

    private String levelByScore(double score) {
        if (score >= 85) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 60) return "待提升";
        return "薄弱";
    }

    private String recommendationByLevel(String level) {
        return switch (level) {
            case "优秀" -> "可安排拓展题与跨章节综合训练。";
            case "良好" -> "建议维持当前节奏，补充中档综合题。";
            case "待提升" -> "建议先进行知识点回顾，再配套针对性练习。";
            default -> "建议优先进行基础知识重建与一对一答疑。";
        };
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void ensureStudentAssignmentAnalysisTable() {
        if (studentAssignmentTableReady) {
            return;
        }
        synchronized (this) {
            if (studentAssignmentTableReady) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS student_assignment_analyses (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        teacher_id INT NOT NULL,
                        course_code VARCHAR(50) NOT NULL,
                        assignment_id INT NOT NULL,
                        student_id INT NOT NULL,
                        answer_count INT NOT NULL,
                        avg_score DECIMAL(6,2) NOT NULL,
                        preparedness_score DECIMAL(6,2) NOT NULL,
                        mastery_level VARCHAR(20) NOT NULL,
                        recommendation VARCHAR(500) NULL,
                        analysis_json LONGTEXT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_student_assignment_analysis (assignment_id, student_id),
                        KEY idx_saa_teacher_course_assignment (teacher_id, course_code, assignment_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            studentAssignmentTableReady = true;
        }
    }

    private void ensureAssignmentAnalysisSnapshotTable() {
        if (assignmentSnapshotTableReady) {
            return;
        }
        synchronized (this) {
            if (assignmentSnapshotTableReady) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS assignment_analysis_snapshots (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        teacher_id INT NOT NULL,
                        course_code VARCHAR(50) NOT NULL,
                        assignment_id INT NOT NULL,
                        assignment_title VARCHAR(255) NOT NULL,
                        overview_json LONGTEXT NULL,
                        distribution_json LONGTEXT NULL,
                        trend_json LONGTEXT NULL,
                        weak_points_json LONGTEXT NULL,
                        student_profiles_json LONGTEXT NULL,
                        ai_recommendation_json LONGTEXT NULL,
                        summary TEXT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
                        UNIQUE KEY uq_assignment_analysis_snapshot (teacher_id, course_code, assignment_id),
                        KEY idx_assignment_analysis_assignment (assignment_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            assignmentSnapshotTableReady = true;
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    private static class StudentAggregate {
        Integer studentId;
        String studentName;
        int answerCount;
        double scoreSum;
        int scoreCount;
        double signalSum;
        int signalCount;
    }

    private static class TrendAggregate {
        int answerCount;
        double scoreSum;
    }
}
