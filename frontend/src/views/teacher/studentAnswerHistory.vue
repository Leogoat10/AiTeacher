<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import { ElMessage } from "element-plus"
import 'katex/dist/katex.min.css'

// 占位符前缀，用于保护 LaTeX 公式和下划线
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const UNDERSCORE_PLACEHOLDER_PREFIX = 'UNDERSCOREBLANK'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()
const underscoreStore: Map<string, string> = new Map()

// 提取并保护 LaTeX 公式和下划线，替换为占位符
const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
  
  // 先处理块级公式 \[...\]
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: true })
    counter++
    return placeholder
  })
  
  // 再处理行内公式 \(...\)
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: false })
    counter++
    return placeholder
  })
  
  // 保护填空题中的下划线（连续的下划线，通常2个或更多）
  text = text.replace(/_{2,}/g, (match) => {
    const placeholder = `${UNDERSCORE_PLACEHOLDER_PREFIX}${underscoreCounter}ENDUNDERSCORE`
    underscoreStore.set(placeholder, match)
    underscoreCounter++
    return placeholder
  })
  
  return text
}

// 渲染被保护的 LaTeX 公式和下划线
const renderProtectedLatex = (html: string): string => {
  // 先渲染 LaTeX 公式
  latexFormulaStore.forEach((data, placeholder) => {
    try {
      const rendered = katex.renderToString(data.formula, {
        displayMode: data.displayMode,
        throwOnError: false,
        output: 'html'
      })
      html = html.replace(new RegExp(placeholder, 'g'), rendered)
    } catch (e) {
      console.error('KaTeX render error:', e, 'Formula:', data.formula)
    }
  })
  
  // 恢复下划线（用带样式的span包裹，避免被当作普通文本处理）
  underscoreStore.forEach((underscores, placeholder) => {
    const styledUnderscores = `<span class="fill-blank">${underscores}</span>`
    html = html.replace(new RegExp(placeholder, 'g'), styledUnderscores)
  })
  
  return html
}

interface StudentAnswer {
  id: number;
  assignmentId: number;
  assignmentTitle: string;
  assignmentContent: string;
  studentAnswer: string;
  aiScore: string;
  aiAnalysis: string;
  submittedAt: string;
}

interface Assignment {
  id: number;
  title: string;
  content: string;
  createdAt: string;
}

interface AssignmentWithStatus extends Assignment {
  answered: boolean;
  answerId?: number;
  studentAnswer?: string;
  aiScore?: string;
  aiAnalysis?: string;
  submittedAt?: string;
}

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const error = ref<string | null>(null)
const studentName = ref('')
const studentId = ref<number>(0)
const courseCode = ref('')
const studentAnswers = ref<StudentAnswer[]>([])
const courseAssignments = ref<Assignment[]>([])
const assignmentsWithStatus = ref<AssignmentWithStatus[]>([])
const filterAssignmentTitle = ref('')
const filterStatus = ref<'all' | 'answered' | 'unanswered'>('all')

// 编辑对话框相关
const editDialogVisible = ref(false)
const editingAnswer = ref<StudentAnswer | null>(null)
const editScore = ref('')
const editAnalysis = ref('')

// 渲染Markdown
function renderMarkdown(text: string): string {
  if (!text) return ''
  // 1. 提取并保护 LaTeX 公式
  const protectedText = protectLatexFormulas(text)
  // 2. 解析 Markdown
  const html = marked(protectedText) as string
  // 3. 渲染被保护的 LaTeX 公式
  const withLatex = renderProtectedLatex(html)
  // 4. 清理 HTML
  return DOMPurify.sanitize(withLatex)
}

// 获取课程的所有题目
async function fetchCourseAssignments() {
  try {
    const res = await apiClient.get(`/assignment/course/${courseCode.value}`)
    courseAssignments.value = res.data as Assignment[]
  } catch (err: any) {
    console.error('获取课程题目失败:', err)
  }
}

// 获取学生的答题历史
async function fetchStudentAnswers() {
  try {
    const res = await apiClient.get(`/studentAnswer/student/${studentId.value}/course/${courseCode.value}`)
    studentAnswers.value = res.data as StudentAnswer[]
  } catch (err: any) {
    console.error('获取答题历史失败:', err)
    studentAnswers.value = []
  }
}

// 合并课程题目和学生答案
function mergeAssignmentsWithAnswers() {
  const answerMap = new Map<number, StudentAnswer>()
  studentAnswers.value.forEach(answer => {
    answerMap.set(answer.assignmentId, answer)
  })
  
  assignmentsWithStatus.value = courseAssignments.value.map(assignment => {
    const answer = answerMap.get(assignment.id)
    
    if (answer) {
      return {
        ...assignment,
        answered: true,
        answerId: answer.id,
        studentAnswer: answer.studentAnswer,
        aiScore: answer.aiScore,
        aiAnalysis: answer.aiAnalysis,
        submittedAt: answer.submittedAt
      }
    } else {
      return {
        ...assignment,
        answered: false
      }
    }
  })
}

// 加载所有数据
async function loadAllData() {
  loading.value = true
  error.value = null
  try {
    await Promise.all([
      fetchCourseAssignments(),
      fetchStudentAnswers()
    ])
    mergeAssignmentsWithAnswers()
  } catch (err: any) {
    if (err.response?.status === 401) {
      error.value = '未登录或会话失效，请重新登录'
      setTimeout(() => router.push('/teacherLogin'), 2000)
    } else {
      error.value = '获取数据失败: ' + (err.response?.data || err.message)
    }
  } finally {
    loading.value = false
  }
}

// 过滤后的题目列表
const filteredAssignments = computed(() => {
  let results = assignmentsWithStatus.value
  
  // 按标题过滤
  if (filterAssignmentTitle.value) {
    results = results.filter(a => 
      a.title.toLowerCase().includes(filterAssignmentTitle.value.toLowerCase())
    )
  }
  
  // 按答题状态过滤
  if (filterStatus.value === 'answered') {
    results = results.filter(a => a.answered)
  } else if (filterStatus.value === 'unanswered') {
    results = results.filter(a => !a.answered)
  }
  
  return results
})

// 打开编辑对话框
function openEditDialog(assignment: AssignmentWithStatus) {
  if (!assignment.answered) {
    ElMessage.warning('该学生尚未提交答案')
    return
  }
  
  // 构造 StudentAnswer 对象
  const answer: StudentAnswer = {
    id: assignment.answerId!,
    assignmentId: assignment.id,
    assignmentTitle: assignment.title,
    assignmentContent: assignment.content,
    studentAnswer: assignment.studentAnswer!,
    aiScore: assignment.aiScore!,
    aiAnalysis: assignment.aiAnalysis!,
    submittedAt: assignment.submittedAt!
  }
  
  editingAnswer.value = answer
  editScore.value = answer.aiScore
  editAnalysis.value = answer.aiAnalysis
  editDialogVisible.value = true
}

// 保存编辑
async function saveEdit() {
  if (!editingAnswer.value) return
  
  loading.value = true
  try {
    await apiClient.put(`/studentAnswer/${editingAnswer.value.id}`, {
      score: editScore.value,
      analysis: editAnalysis.value
    })
    
    // 更新本地数据
    const index = studentAnswers.value.findIndex(a => a.id === editingAnswer.value!.id)
    if (index !== -1) {
      studentAnswers.value[index].aiScore = editScore.value
      studentAnswers.value[index].aiAnalysis = editAnalysis.value
    }
    
    // 更新合并后的数据
    const assignmentIndex = assignmentsWithStatus.value.findIndex(
      a => a.answerId === editingAnswer.value!.id
    )
    if (assignmentIndex !== -1) {
      assignmentsWithStatus.value[assignmentIndex].aiScore = editScore.value
      assignmentsWithStatus.value[assignmentIndex].aiAnalysis = editAnalysis.value
    }
    
    editDialogVisible.value = false
    ElMessage.success('更新成功')
  } catch (err: any) {
    ElMessage.error('更新失败: ' + (err.response?.data || err.message))
  } finally {
    loading.value = false
  }
}

// 查看详情
function viewDetail(assignment: AssignmentWithStatus) {
  if (!assignment.answered) {
    ElMessage.warning('该学生尚未提交答案')
    return
  }
  
  router.push({
    path: `/teacherViewAnswerDetail/${assignment.answerId}`,
    query: {
      studentId: studentId.value.toString(),
      courseCode: courseCode.value,
      studentName: studentName.value
    }
  })
}

// 格式化时间
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 返回学生列表
function goBack() {
  router.push('/answerManagement')
}

onMounted(() => {
  studentId.value = Number(route.params.studentId)
  courseCode.value = route.params.courseCode as string
  studentName.value = route.query.studentName as string || '学生'
  
  if (studentId.value && courseCode.value) {
    loadAllData()
  } else {
    error.value = '参数错误'
  }
})
</script>

<template>
  <div class="answer-history-container">
    <div class="header">
      <h1>{{ studentName }} - 答题历史</h1>
      <button @click="goBack" class="back-btn">返回学生列表</button>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>
    <div v-if="loading" class="loading">加载中...</div>

    <div v-if="!loading && !error" class="content">
      <!-- 过滤器 -->
      <div class="filters">
        <div class="filter-item">
          <label>题目标题：</label>
          <input v-model="filterAssignmentTitle" type="text" placeholder="搜索题目标题">
        </div>
        <div class="filter-item">
          <label>答题状态：</label>
          <select v-model="filterStatus" class="filter-select">
            <option value="all">全部</option>
            <option value="answered">已答题</option>
            <option value="unanswered">未答题</option>
          </select>
        </div>
      </div>

      <!-- 题目列表表格 -->
      <div class="table-container">
        <table v-if="filteredAssignments.length > 0">
          <thead>
            <tr>
              <th>题目标题</th>
              <th>状态</th>
              <th>评分</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="assignment in filteredAssignments" :key="assignment.id" :class="{ 'unanswered-row': !assignment.answered }">
              <td>{{ assignment.title }}</td>
              <td>
                <span v-if="assignment.answered" class="status-badge status-answered">已答题</span>
                <span v-else class="status-badge status-unanswered">未答题</span>
              </td>
              <td>{{ assignment.answered ? assignment.aiScore : '-' }}</td>
              <td>{{ assignment.answered ? formatDate(assignment.submittedAt!) : '-' }}</td>
              <td>
                <button 
                  v-if="assignment.answered" 
                  @click="viewDetail(assignment)" 
                  class="btn-view"
                >查看详情</button>
                <button 
                  v-if="assignment.answered" 
                  @click="openEditDialog(assignment)" 
                  class="btn-edit"
                >修改评分</button>
                <span v-if="!assignment.answered" class="no-action">-</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="no-data">
          没有找到匹配的题目
        </div>
      </div>
    </div>

    <!-- 编辑对话框 -->
    <div v-if="editDialogVisible" class="modal-overlay" @click="editDialogVisible = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h2>修改评分</h2>
          <button @click="editDialogVisible = false" class="close-btn">&times;</button>
        </div>
        <div class="modal-body" v-if="editingAnswer">
          <div class="form-group">
            <label>学生：{{ studentName }}</label>
          </div>
          <div class="form-group">
            <label>题目：{{ editingAnswer.assignmentTitle }}</label>
          </div>
          <div class="form-group">
            <label>题目内容：</label>
            <div class="content-box markdown-content" v-html="renderMarkdown(editingAnswer.assignmentContent)"></div>
          </div>
          <div class="form-group">
            <label>学生答案：</label>
            <div class="content-box">{{ editingAnswer.studentAnswer }}</div>
          </div>
          <div class="form-group">
            <label>评分：</label>
            <input v-model="editScore" type="text" placeholder="例如：85/100">
          </div>
          <div class="form-group">
            <label>分析：</label>
            <textarea v-model="editAnalysis" rows="6" placeholder="请输入对学生答案的分析"></textarea>
          </div>
          <div class="modal-footer">
            <button @click="editDialogVisible = false" class="btn-cancel">取消</button>
            <button @click="saveEdit" class="btn-save">保存</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.answer-history-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.header h1 {
  font-size: 28px;
  color: #333;
}

.back-btn {
  padding: 10px 20px;
  background-color: #6c757d;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.back-btn:hover {
  background-color: #5a6268;
}

.error-message {
  background-color: #f8d7da;
  color: #721c24;
  padding: 15px;
  border-radius: 5px;
  margin-bottom: 20px;
}

.loading {
  text-align: center;
  padding: 40px;
  font-size: 18px;
  color: #666;
}

.filters {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 5px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-item label {
  font-weight: bold;
}

.filter-item input,
.filter-item .filter-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.filter-item input {
  min-width: 300px;
}

.filter-item .filter-select {
  min-width: 150px;
  cursor: pointer;
}

.table-container {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  background-color: #007bff;
  color: white;
}

th {
  padding: 15px;
  text-align: left;
  font-weight: bold;
}

tbody tr {
  border-bottom: 1px solid #dee2e6;
}

tbody tr:hover {
  background-color: #f8f9fa;
}

tbody tr.unanswered-row {
  background-color: #fef8f8;
}

tbody tr.unanswered-row:hover {
  background-color: #fef0f0;
}

td {
  padding: 12px 15px;
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-answered {
  background-color: #d4edda;
  color: #155724;
}

.status-unanswered {
  background-color: #f8d7da;
  color: #721c24;
}

.no-action {
  color: #999;
  font-style: italic;
}

.btn-view, .btn-edit {
  padding: 6px 12px;
  margin-right: 8px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.btn-view {
  background-color: #17a2b8;
  color: white;
}

.btn-view:hover {
  background-color: #138496;
}

.btn-edit {
  background-color: #ffc107;
  color: #333;
}

.btn-edit:hover {
  background-color: #e0a800;
}

.no-data {
  padding: 40px;
  text-align: center;
  color: #666;
  font-size: 16px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  max-width: 800px;
  width: 90%;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #dee2e6;
}

.modal-header h2 {
  margin: 0;
  font-size: 22px;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  cursor: pointer;
  color: #666;
  line-height: 1;
}

.close-btn:hover {
  color: #333;
}

.modal-body {
  padding: 20px;
}

.content-box {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 5px;
  border: 1px solid #dee2e6;
  white-space: pre-wrap;
  line-height: 1.6;
}

.markdown-content {
  white-space: normal;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 16px 0 8px 0;
  font-weight: bold;
  color: #333;
}

.markdown-content :deep(h1) { font-size: 1.8em; }
.markdown-content :deep(h2) { font-size: 1.5em; }
.markdown-content :deep(h3) { font-size: 1.3em; }

.markdown-content :deep(p) {
  margin: 8px 0;
  line-height: 1.6;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 4px 0;
}

.markdown-content :deep(code) {
  background-color: #e9ecef;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background-color: #2d2d2d;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 5px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: inherit;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #007bff;
  padding-left: 12px;
  margin: 12px 0;
  color: #666;
  font-style: italic;
}

.markdown-content :deep(a) {
  color: #007bff;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  border: 1px solid #dee2e6;
  padding: 8px;
  text-align: left;
}

.markdown-content :deep(table th) {
  background-color: #e9ecef;
  font-weight: bold;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 12px 0;
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid #dee2e6;
  margin: 16px 0;
}

.markdown-content :deep(strong) {
  font-weight: bold;
}

.markdown-content :deep(em) {
  font-style: italic;
}

/* 填空题下划线样式 */
.markdown-content :deep(.fill-blank) {
  display: inline;
  font-weight: normal;
  font-style: normal;
  text-decoration: underline;
  text-decoration-style: solid;
  text-decoration-thickness: 1px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
  color: #333;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
}

.form-group textarea {
  resize: vertical;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.btn-cancel, .btn-save {
  padding: 10px 20px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-cancel {
  background-color: #6c757d;
  color: white;
}

.btn-cancel:hover {
  background-color: #5a6268;
}

.btn-save {
  background-color: #28a745;
  color: white;
}

.btn-save:hover {
  background-color: #218838;
}
</style>
