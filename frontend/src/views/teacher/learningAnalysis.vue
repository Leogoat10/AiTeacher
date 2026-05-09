<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

interface Course {
  courseCode: string
  courseName: string
}

interface AssignmentOption {
  id: number
  title: string
}

interface StudentOption {
  studentId: number
  studentName: string
  studentNumber: string
  answerCount: number
  hasSavedAnalysis?: boolean
}

interface StudentProfile {
  studentId: number
  studentName: string
  answerCount: number
  avgScore: number
  aiScore?: string | number
  scoreDisplay?: string
  masteryLevel: string
  recommendation: string
  aiAnalysis?: string
}

interface WeakPoint {
  knowledgePoint: string
  frequency: number
}

interface AiRecommendation {
  overallInsight: string
  teacherSuggestions: string[]
  studentSuggestions: string[]
  resourceSuggestions: string[]
  modelName: string
}

const router = useRouter()
const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()

marked.setOptions({
  breaks: true,
  gfm: true
})

const autoWrapPlainMathExpressions = (text: string, registerInlineFormula: (formula: string) => string): string => {
  if (!text) return text

  const exprRegex = /([A-Za-zΑ-Ωα-ωσθμλπΔΣΩ][A-Za-z0-9Α-Ωα-ωσθμλπΔΣΩ_]*(?:\([^()\n]{1,40}\))?\s*=\s*[^,，。；;\n]{1,120})/g
  return text.split('\n').map((line) => {
    if (!line || line.includes(LATEX_PLACEHOLDER_PREFIX) || line.includes('\\(') || line.includes('\\[') || line.includes('`') || /^\s*([#>*-]|\d+\.)\s+/.test(line)) {
      return line
    }
    return line.replace(exprRegex, (match) => {
      const normalized = match.trim()
      const hasMathToken = /[+\-*/^_()]|[Α-Ωα-ωσθμλπΔΣΩ]|e\^\{?[-+]?[A-Za-z0-9]/.test(normalized)
      return hasMathToken ? registerInlineFormula(normalized) : match
    })
  }).join('\n')
}

const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  let counter = 0
  const registerFormula = (formula: string, displayMode: boolean) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}${displayMode ? 'DISPLAY' : 'INLINE'}${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode })
    counter += 1
    return placeholder
  }
  const registerInlineFormula = (formula: string) => registerFormula(formula, false)

  let replaced = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => registerFormula(formula, true))
  replaced = replaced.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => registerInlineFormula(formula))
  replaced = replaced.replace(/\$\$([\s\S]*?)\$\$/g, (_match, formula) => registerFormula(formula, true))
  replaced = replaced.replace(/\$([^\$\n]+?)\$/g, (match, formula) => (/[A-Za-z\\{}\^_=+\-*/()]/.test(formula) ? registerInlineFormula(formula) : match))
  return autoWrapPlainMathExpressions(replaced, registerInlineFormula)
}

const renderProtectedLatex = (html: string): string => {
  let renderedHtml = html
  latexFormulaStore.forEach((data, placeholder) => {
    try {
      const rendered = katex.renderToString(data.formula, { displayMode: data.displayMode, throwOnError: false, output: 'html' })
      renderedHtml = renderedHtml.replace(new RegExp(placeholder, 'g'), rendered)
    } catch {
      // ignore single formula render failure
    }
  })
  return renderedHtml
}

const renderMarkdown = (content: string): string => {
  if (!content) return ''
  const protectedText = protectLatexFormulas(content)
  const html = marked(protectedText) as string
  const withLatex = renderProtectedLatex(html)
  return DOMPurify.sanitize(withLatex, {
    ADD_TAGS: ['math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt', 'mroot', 'mtext', 'annotation', 'munderover', 'mtable', 'mtr', 'mtd'],
    ADD_ATTR: ['xmlns', 'aria-hidden', 'focusable']
  })
}

const loading = ref(false)
const detailLoading = ref(false)
const error = ref<string | null>(null)
const courses = ref<Course[]>([])
const assignments = ref<AssignmentOption[]>([])
const studentsForPick = ref<StudentOption[]>([])
const selectedStudentIds = ref<number[]>([])
const selectedCourse = ref('')
const selectedAssignmentId = ref<string>('')
const showStudentPicker = ref(true)
const hasSavedData = ref(false)
const analysisLoaded = ref(false)

const overview = ref<any>(null)
const distribution = ref<any>(null)
const weakPoints = ref<WeakPoint[]>([])
const students = ref<StudentProfile[]>([])
const selectedStudent = ref<any>(null)
const aiRecommendation = ref<AiRecommendation | null>(null)

const displayOverview = computed(() => ({
  totalStudents: 0,
  totalAnswers: 0,
  avgScore: 0,
  masteryRate: 0,
  answerCoverage: 0,
  riskStudentCount: 0,
  ...(overview.value || {})
}))

const displayDistribution = computed(() => ({
  excellent: 0,
  good: 0,
  improve: 0,
  weak: 0,
  ...(distribution.value || {})
}))

const displayAiRecommendation = computed(() => ({
  overallInsight: '暂无AI学习建议，请先执行学情分析。',
  teacherSuggestions: [] as string[],
  studentSuggestions: [] as string[],
  resourceSuggestions: [] as string[],
  modelName: '--',
  ...(aiRecommendation.value || {})
}))

const renderedStudentAiAnalysis = computed(() => renderMarkdown(selectedStudent.value?.aiAnalysis || '暂无AI分析'))

const levelClass = (level: string): string => {
  if (level === '优秀') return 'level-excellent'
  if (level === '良好') return 'level-good'
  if (level === '待提升') return 'level-improve'
  return 'level-weak'
}

const formatStudentScore = (student: StudentProfile | null | undefined): string => {
  if (!student) return '--'
  const scoreDisplay = String(student.scoreDisplay ?? '').trim()
  if (scoreDisplay) return scoreDisplay

  const rawAiScore = String(student.aiScore ?? '').trim()
  if (rawAiScore.includes('/')) return rawAiScore
  const aiScoreNumber = Number(rawAiScore)
  if (rawAiScore && Number.isFinite(aiScoreNumber)) return `${aiScoreNumber}/100`

  const avgScoreNumber = Number(student.avgScore)
  if (Number.isFinite(avgScoreNumber)) return `${avgScoreNumber}/100`
  return '--'
}

const allChecked = computed(() => {
  if (studentsForPick.value.length === 0) return false
  return studentsForPick.value.every(item => selectedStudentIds.value.includes(item.studentId))
})

function resetAnalysisResult() {
  overview.value = null
  distribution.value = null
  weakPoints.value = []
  students.value = []
  selectedStudent.value = null
  aiRecommendation.value = null
  hasSavedData.value = false
  analysisLoaded.value = false
}

function getEmptyStateText() {
  if (!selectedAssignmentId.value) return '请选择作业'
  if (!analysisLoaded.value) return '加载中...'
  return '无数据'
}

async function fetchCourses() {
  loading.value = true
  error.value = null
  try {
    const res = await apiClient.get('/course/list')
    courses.value = res.data as Course[]
    if (courses.value.length > 0) {
      selectedCourse.value = courses.value[0].courseCode
      await fetchAssignments()
    }
  } catch (err: any) {
    if (err.response?.status === 401) {
      error.value = '未登录或会话失效，请重新登录'
      setTimeout(() => router.push('/teacherLogin'), 1200)
    } else {
      error.value = '获取课程失败: ' + (err.response?.data || err.message)
    }
  } finally {
    loading.value = false
  }
}

async function fetchAssignments() {
  assignments.value = []
  selectedAssignmentId.value = ''
  studentsForPick.value = []
  selectedStudentIds.value = []
  resetAnalysisResult()
  if (!selectedCourse.value) return

  try {
    const res = await apiClient.get(`/assignment/course/${selectedCourse.value}`)
    assignments.value = (res.data || []).map((item: any) => ({
      id: Number(item.id),
      title: item.title
    }))
    if (assignments.value.length > 0) {
      selectedAssignmentId.value = String(assignments.value[0].id)
      await fetchStudentsForPick()
    }
  } catch (err: any) {
    error.value = '获取作业列表失败: ' + (err.response?.data || err.message)
  }
}

async function fetchStudentsForPick() {
  studentsForPick.value = []
  selectedStudentIds.value = []
  resetAnalysisResult()
  if (!selectedCourse.value || !selectedAssignmentId.value) return

  try {
    const res = await apiClient.get(`/learningAnalysis/course/${selectedCourse.value}/student-list`, {
      params: { assignmentId: Number(selectedAssignmentId.value) }
    })
    studentsForPick.value = (res.data || []) as StudentOption[]
    await loadSavedStudentAnalyses()
    await loadLatestSavedAnalysisResult()
  } catch (err: any) {
    error.value = '获取学生列表失败: ' + (err.response?.data || err.message)
    analysisLoaded.value = true
  }
}

async function loadSavedStudentAnalyses() {
  if (!selectedCourse.value || !selectedAssignmentId.value) {
    students.value = []
    return
  }
  try {
    const res = await apiClient.get(
      `/learningAnalysis/course/${selectedCourse.value}/assignment/${selectedAssignmentId.value}/saved`
    )
    const saved = (res.data || []) as StudentProfile[]
    students.value = saved
    const savedIdSet = new Set(saved.map(s => Number(s.studentId)))
    studentsForPick.value = studentsForPick.value.map(item => ({
      ...item,
      hasSavedAnalysis: savedIdSet.has(item.studentId)
    }))
  } catch (err: any) {
    error.value = '获取已保存分析失败: ' + (err.response?.data || err.message)
  }
}

async function loadLatestSavedAnalysisResult() {
  if (!selectedCourse.value || !selectedAssignmentId.value) return
  try {
    const res = await apiClient.get(
      `/learningAnalysis/course/${selectedCourse.value}/assignment/${selectedAssignmentId.value}/latest`
    )
    if (res.data?.hasData) {
      hasSavedData.value = true
      overview.value = res.data?.overview || null
      distribution.value = res.data?.distribution || null
      weakPoints.value = res.data?.weakKnowledgePoints || []
      aiRecommendation.value = res.data?.aiRecommendation || null
      if (Array.isArray(res.data?.studentProfiles) && res.data.studentProfiles.length > 0) {
        students.value = res.data.studentProfiles as StudentProfile[]
      }
    } else {
      hasSavedData.value = false
    }
  } catch (err: any) {
    error.value = '获取已分析结果失败: ' + (err.response?.data || err.message)
    hasSavedData.value = false
  } finally {
    analysisLoaded.value = true
  }
}

function toggleAllStudents() {
  if (allChecked.value) {
    selectedStudentIds.value = []
    return
  }
  selectedStudentIds.value = studentsForPick.value.map(s => s.studentId)
}

function toggleStudentPicker() {
  showStudentPicker.value = !showStudentPicker.value
}

async function runManualAnalysis() {
  if (!selectedCourse.value) {
    error.value = '请先选择课程'
    return
  }
  if (!selectedAssignmentId.value) {
    error.value = '请先选择作业'
    return
  }
  if (selectedStudentIds.value.length === 0) {
    error.value = '请至少勾选一名学生'
    return
  }

  loading.value = true
  error.value = null
  selectedStudent.value = null
  try {
    const res = await apiClient.post(`/learningAnalysis/course/${selectedCourse.value}/analyze`, {
      assignmentId: Number(selectedAssignmentId.value),
      studentIds: selectedStudentIds.value
    })
    overview.value = res.data?.overview || null
    distribution.value = res.data?.distribution || null
    weakPoints.value = res.data?.weakKnowledgePoints || []
    students.value = res.data?.studentProfiles || []
    aiRecommendation.value = res.data?.aiRecommendation || null
    hasSavedData.value = true
    await loadSavedStudentAnalyses()
  } catch (err: any) {
    error.value = '学情分析失败: ' + (err.response?.data?.message || err.response?.data || err.message)
  } finally {
    loading.value = false
  }
}

async function viewStudentProfile(studentId: number) {
  if (!selectedAssignmentId.value) return
  detailLoading.value = true
  try {
    const res = await apiClient.get(`/learningAnalysis/course/${selectedCourse.value}/students/${studentId}`, {
      params: { assignmentId: Number(selectedAssignmentId.value) }
    })
    selectedStudent.value = res.data?.profile || null
  } catch (err: any) {
    error.value = '加载学生画像失败: ' + (err.response?.data?.message || err.response?.data || err.message)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchCourses()
})
</script>

<template>
  <div class="learning-analysis-container">
    <div class="header">
      <h1>学情分析</h1>
      <button class="back-btn" @click="router.push('/teacherInfo')">返回首页</button>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>
    <div v-if="loading" class="loading">分析中...</div>

    <div class="panel">
      <h3>手动触发分析</h3>
      <div class="toolbar">
        <label>课程：</label>
        <select v-model="selectedCourse" @change="fetchAssignments">
          <option v-for="course in courses" :key="course.courseCode" :value="course.courseCode">
            {{ course.courseName }} ({{ course.courseCode }})
          </option>
        </select>

        <label>作业：</label>
        <select v-model="selectedAssignmentId" @change="fetchStudentsForPick">
          <option value="">请选择作业</option>
          <option v-for="assignment in assignments" :key="assignment.id" :value="String(assignment.id)">
            {{ assignment.title }}
          </option>
        </select>

        <button class="refresh-btn" @click="runManualAnalysis">开始学情分析</button>
      </div>

      <div class="picker-header">
        <h4>学生选择</h4>
        <button class="collapse-btn" @click="toggleStudentPicker">
          {{ showStudentPicker ? '收起' : '展开' }}
        </button>
      </div>

      <table v-if="showStudentPicker && studentsForPick.length > 0">
        <thead>
          <tr>
            <th><input type="checkbox" :checked="allChecked" @change="toggleAllStudents"></th>
            <th>学生</th>
            <th>学号</th>
            <th>本次作业答题数</th>
            <th>历史分析</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stu in studentsForPick" :key="stu.studentId">
            <td>
              <input
                type="checkbox"
                :value="stu.studentId"
                v-model="selectedStudentIds"
              >
            </td>
            <td>{{ stu.studentName }}</td>
            <td>{{ stu.studentNumber }}</td>
            <td>{{ stu.answerCount }}</td>
            <td>{{ stu.hasSavedAnalysis ? '已分析' : '未分析' }}</td>
            <td>
              <button class="mini-btn" @click="viewStudentProfile(stu.studentId)">查看分析</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="showStudentPicker" class="empty">
        {{ selectedAssignmentId ? '当前作业暂无学生数据' : '请选择作业后勾选学生，再手动触发分析' }}
      </div>
    </div>

      <div class="overview-grid">
        <div class="card"><div class="card-title">学生总数</div><div class="card-value">{{ hasSavedData ? displayOverview.totalStudents : getEmptyStateText() }}</div></div>
        <div class="card"><div class="card-title">答题总数</div><div class="card-value">{{ hasSavedData ? displayOverview.totalAnswers : getEmptyStateText() }}</div></div>
        <div class="card"><div class="card-title">预备知识达标率</div><div class="card-value">{{ hasSavedData ? (displayOverview.masteryRate + '%') : getEmptyStateText() }}</div></div>
        <div class="card"><div class="card-title">答题覆盖率</div><div class="card-value">{{ hasSavedData ? (displayOverview.answerCoverage + '%') : getEmptyStateText() }}</div></div>
        <div class="card"><div class="card-title">风险学生数</div><div class="card-value danger">{{ hasSavedData ? displayOverview.riskStudentCount : getEmptyStateText() }}</div></div>
      </div>

      <div class="panels">
        <div class="panel">
          <h3>掌握度分层</h3>
          <div class="level-row"><span>优秀</span><b>{{ hasSavedData ? displayDistribution.excellent : getEmptyStateText() }}</b></div>
          <div class="level-row"><span>良好</span><b>{{ hasSavedData ? displayDistribution.good : getEmptyStateText() }}</b></div>
          <div class="level-row"><span>待提升</span><b>{{ hasSavedData ? displayDistribution.improve : getEmptyStateText() }}</b></div>
          <div class="level-row"><span>薄弱</span><b>{{ hasSavedData ? displayDistribution.weak : getEmptyStateText() }}</b></div>
        </div>
        <div class="panel">
          <h3>薄弱知识点（Top）</h3>
          <div v-if="weakPoints.length === 0" class="empty">暂无明显集中薄弱知识点</div>
          <div v-for="point in weakPoints" :key="point.knowledgePoint" class="level-row">
            <span>{{ point.knowledgePoint }}</span>
            <b>{{ point.frequency }}</b>
          </div>
        </div>
      </div>

      <div class="panel">
        <h3>AI学习建议</h3>
        <div class="ai-summary"><b>结论：</b>{{ displayAiRecommendation.overallInsight }}</div>
        <div class="ai-grid">
          <div>
            <h4>教师侧建议</h4>
            <ol v-if="displayAiRecommendation.teacherSuggestions.length > 0"><li v-for="item in displayAiRecommendation.teacherSuggestions" :key="'t-' + item">{{ item }}</li></ol>
            <div v-else class="empty">暂无教师侧建议</div>
          </div>
          <div>
            <h4>学生侧建议</h4>
            <ol v-if="displayAiRecommendation.studentSuggestions.length > 0"><li v-for="item in displayAiRecommendation.studentSuggestions" :key="'s-' + item">{{ item }}</li></ol>
            <div v-else class="empty">暂无学生侧建议</div>
          </div>
        </div>
        <div>
          <h4>资源建议</h4>
          <ol v-if="displayAiRecommendation.resourceSuggestions.length > 0"><li v-for="item in displayAiRecommendation.resourceSuggestions" :key="'r-' + item">{{ item }}</li></ol>
          <div v-else class="empty">暂无资源建议</div>
        </div>
        <div class="ai-model">建议来源：{{ displayAiRecommendation.modelName }}</div>
      </div>

      <div class="panel">
        <h3>学生学情画像</h3>
        <table v-if="students.length > 0">
          <thead>
            <tr><th>学生</th><th>答题数</th><th>得分/总分</th><th>掌握层级</th><th>建议</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="stu in students" :key="stu.studentId">
              <td>{{ stu.studentName }}</td>
              <td>{{ stu.answerCount }}</td>
              <td>{{ formatStudentScore(stu) }}</td>
              <td><span class="level-tag" :class="levelClass(stu.masteryLevel)">{{ stu.masteryLevel }}</span></td>
              <td>{{ stu.recommendation }}</td>
              <td><button class="mini-btn" @click="viewStudentProfile(stu.studentId)">查看分析</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="selectedStudent" class="panel">
        <h3>学生分析详情</h3>
        <div v-if="detailLoading" class="loading">加载中...</div>
        <div v-else class="profile-grid">
          <div><b>学生：</b>{{ selectedStudent.studentName }}</div>
          <div><b>答题数：</b>{{ selectedStudent.answerCount }}</div>
          <div><b>得分/总分：</b>{{ formatStudentScore(selectedStudent) }}</div>
          <div><b>掌握层级：</b>{{ selectedStudent.masteryLevel }}</div>
          <div><b>学习建议：</b>{{ selectedStudent.recommendation }}</div>
        </div>
        <div class="student-ai-analysis">
          <b>AI分析：</b>
          <div class="analysis-box" v-html="renderedStudentAiAnalysis"></div>
        </div>
      </div>
  </div>
</template>

<style scoped>
.learning-analysis-container { max-width: 1300px; margin: 0 auto; padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.back-btn { background: #6c757d; color: #fff; border: none; padding: 8px 14px; border-radius: 4px; cursor: pointer; }
.error-message { background: #f8d7da; color: #721c24; padding: 12px; border-radius: 6px; margin-bottom: 12px; }
.loading { text-align: center; color: #666; padding: 20px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.toolbar select { min-width: 240px; padding: 8px; border: 1px solid #ddd; border-radius: 4px; }
.refresh-btn, .mini-btn { background: #007bff; color: #fff; border: none; border-radius: 4px; cursor: pointer; padding: 8px 12px; }
.picker-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.collapse-btn { background: #6b7280; color: #fff; border: none; border-radius: 4px; padding: 6px 10px; cursor: pointer; }
.overview-grid { display: grid; grid-template-columns: repeat(6, minmax(120px, 1fr)); gap: 10px; margin-bottom: 14px; }
.card { background: #fff; border: 1px solid #ececec; border-radius: 8px; padding: 10px; }
.card-title { color: #6b7280; font-size: 13px; }
.card-value { margin-top: 4px; font-size: 22px; font-weight: 700; color: #111827; }
.card-value.danger { color: #dc2626; }
.panels { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.panel { background: #fff; border: 1px solid #ececec; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.panel h3 { margin: 0 0 10px; color: #111827; }
.level-row { display: flex; justify-content: space-between; border-bottom: 1px dashed #e5e7eb; padding: 6px 0; }
.empty { color: #6b7280; padding: 12px 0; }
table { width: 100%; border-collapse: collapse; }
th, td { border-bottom: 1px solid #eee; padding: 8px; text-align: left; }
th { background: #f8fafc; }
.level-tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.level-excellent { background: #dcfce7; color: #166534; }
.level-good { background: #dbeafe; color: #1e3a8a; }
.level-improve { background: #fef3c7; color: #92400e; }
.level-weak { background: #fee2e2; color: #991b1b; }
.profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; }
.student-ai-analysis { margin-top: 12px; }
.analysis-box { margin-top: 6px; background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 6px; padding: 10px; line-height: 1.6; color: #111827; }
.ai-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.ai-summary, .ai-model { margin-bottom: 10px; color: #374151; }
@media (max-width: 1100px) {
  .overview-grid { grid-template-columns: repeat(3, minmax(120px, 1fr)); }
  .panels { grid-template-columns: 1fr; }
  .ai-grid { grid-template-columns: 1fr; }
}
</style>
