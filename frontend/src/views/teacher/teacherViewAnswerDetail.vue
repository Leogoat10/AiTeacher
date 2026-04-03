<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

const router = useRouter()
const route = useRoute()

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const UNDERSCORE_PLACEHOLDER_PREFIX = 'UNDERSCOREBLANK'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()
const underscoreStore: Map<string, string> = new Map()

const autoWrapPlainMathExpressions = (text: string, registerInlineFormula: (formula: string) => string): string => {
  if (!text) return text
  const exprRegex = /([A-Za-zΑ-Ωα-ωσθμλπΔΣΩ][A-Za-z0-9Α-Ωα-ωσθμλπΔΣΩ_]*(?:\([^()\n]{1,40}\))?\s*=\s*[^,，。；;\n]{1,120})/g
  return text.split('\n').map((line) => {
    if (
      !line ||
      line.includes(LATEX_PLACEHOLDER_PREFIX) ||
      line.includes('\\(') ||
      line.includes('\\[') ||
      line.includes('`') ||
      /^\s*([#>*-]|\d+\.)\s+/.test(line)
    ) {
      return line
    }
    return line.replace(exprRegex, (match) => {
      const normalized = match.trim()
      const hasMathToken = /[+\-*/^_()]|[Α-Ωα-ωσθμλπΔΣΩ]|e\^\{?[-+]?[A-Za-z0-9]/.test(normalized)
      if (!hasMathToken) return match
      return registerInlineFormula(normalized)
    })
  }).join('\n')
}

const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
  const registerFormula = (formula: string, displayMode: boolean) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}${displayMode ? 'DISPLAY' : 'INLINE'}${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode })
    counter++
    return placeholder
  }
  const registerInlineFormula = (formula: string) => registerFormula(formula, false)
  
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (_match, formula) => {
    return registerFormula(formula, true)
  })
  
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (_match, formula) => {
    return registerInlineFormula(formula)
  })

  // 处理双美元符号块级公式 $$...$$
  text = text.replace(/\$\$([\s\S]*?)\$\$/g, (_match, formula) => {
    return registerFormula(formula, true)
  })

  // 处理单美元符号行内公式 $...$（避免误匹配货币符号）
  text = text.replace(/\$([^\$\n]+?)\$/g, (match, formula) => {
    // 检查是否包含数学字符，避免误判
    if (/[A-Za-z\\{}\^_=+\-*/()]/.test(formula)) {
      return registerInlineFormula(formula)
    }
    return match
  })

  text = autoWrapPlainMathExpressions(text, registerInlineFormula)
  
  text = text.replace(/_{2,}/g, (match) => {
    const placeholder = `${UNDERSCORE_PLACEHOLDER_PREFIX}${underscoreCounter}ENDUNDERSCORE`
    underscoreStore.set(placeholder, match)
    underscoreCounter++
    return placeholder
  })
  
  return text
}

const renderProtectedLatex = (html: string): string => {
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
  
  underscoreStore.forEach((underscores, placeholder) => {
    const styledUnderscores = `<span class="fill-blank">${underscores}</span>`
    html = html.replace(new RegExp(placeholder, 'g'), styledUnderscores)
  })
  
  return html
}

interface StudentAnswer {
  id: number
  assignmentId: number
  assignmentTitle: string
  assignmentContent: string
  studentAnswer: string
  aiScore: string
  aiAnalysis: string
  evaluationJson?: string
  gradingStatus?: string
  gradingError?: string
  submittedAt: string
}

const loading = ref(false)
const answerDetail = ref<StudentAnswer | null>(null)
const studentName = ref('')
const studentId = ref<number>(0)
const courseCode = ref('')
const editMode = ref(false)
const editScore = ref('')
const editAnalysis = ref('')
const regrading = ref(false)
let regradePollTimer: number | null = null

const parsedEvaluation = computed(() => {
  if (!answerDetail.value?.evaluationJson) return null
  try {
    return JSON.parse(answerDetail.value.evaluationJson)
  } catch {
    return null
  }
})

marked.setOptions({
  breaks: true,
  gfm: true
})

const renderMarkdown = (content: string): string => {
  if (!content) return ''
  const protectedText = protectLatexFormulas(content)
  const html = marked(protectedText) as string
  const withLatex = renderProtectedLatex(html)
  // 配置 DOMPurify 允许 KaTeX 生成的标签和属性
  return DOMPurify.sanitize(withLatex, {
    ADD_TAGS: ['math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub', 'mfrac', 'msqrt', 'mroot', 'mtext', 'annotation', 'munderover', 'mtable', 'mtr', 'mtd'],
    ADD_ATTR: ['xmlns', 'aria-hidden', 'focusable']
  })
}

const loadAnswerDetail = async () => {
  loading.value = true
  try {
    const answerId = Number(route.params.answerId)
    const res = await apiClient.get(`/studentAnswer/student/${studentId.value}/course/${courseCode.value}`)
    const answers = res.data as StudentAnswer[]
    const found = answers.find(a => a.id === answerId)
    
    if (found) {
      answerDetail.value = found
      editScore.value = found.aiScore
      editAnalysis.value = found.aiAnalysis
      if (found.gradingStatus === 'PENDING' || found.gradingStatus === 'RUNNING') {
        regrading.value = true
        stopRegradePolling()
        regradePollTimer = window.setInterval(() => {
          pollAnswerStatus()
        }, 4000)
      }
    } else {
      ElMessage.error('未找到答题记录')
      goBack()
    }
  } catch (err: any) {
    console.error('加载答题详情失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/teacherLogin')
    } else {
      ElMessage.error('加载答题详情失败')
      goBack()
    }
  } finally {
    loading.value = false
  }
}

const toggleEditMode = () => {
  editMode.value = !editMode.value
  if (!editMode.value && answerDetail.value) {
    editScore.value = answerDetail.value.aiScore
    editAnalysis.value = answerDetail.value.aiAnalysis
  }
}

const saveEdit = async () => {
  if (!answerDetail.value) return
  
  try {
    await ElMessageBox.confirm(
      '确认修改该学生的评分和分析吗？',
      '确认修改',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  loading.value = true
  try {
    await apiClient.put(`/studentAnswer/${answerDetail.value.id}`, {
      score: editScore.value,
      analysis: editAnalysis.value
    })
    
    answerDetail.value.aiScore = editScore.value
    answerDetail.value.aiAnalysis = editAnalysis.value
    editMode.value = false
    ElMessage.success('修改成功')
  } catch (err: any) {
    console.error('修改失败:', err)
    ElMessage.error('修改失败: ' + (err.response?.data || err.message))
  } finally {
    loading.value = false
  }
}

const stopRegradePolling = () => {
  if (regradePollTimer !== null) {
    window.clearInterval(regradePollTimer)
    regradePollTimer = null
  }
}

const pollAnswerStatus = async () => {
  if (!answerDetail.value) return
  try {
    const res = await apiClient.get(`/studentAnswer/${answerDetail.value.id}/status`)
    if (res.data?.success) {
      answerDetail.value.gradingStatus = res.data.gradingStatus
      answerDetail.value.aiScore = res.data.score || ''
      answerDetail.value.aiAnalysis = res.data.analysis || ''
      answerDetail.value.gradingError = res.data.gradingError || ''
      answerDetail.value.evaluationJson = res.data.evaluationJson || ''
      if (res.data.gradingStatus === 'SUCCESS' || res.data.gradingStatus === 'FAILED') {
        stopRegradePolling()
        regrading.value = false
      }
    }
  } catch (err) {
    console.error('轮询重判状态失败:', err)
  }
}

const triggerRegrade = async () => {
  if (!answerDetail.value) return
  loading.value = true
  try {
    const res = await apiClient.post(`/studentAnswer/${answerDetail.value.id}/regrade`)
    if (res.data?.success) {
      ElMessage.success('已触发重新判题')
      answerDetail.value.gradingStatus = 'PENDING'
      answerDetail.value.aiScore = ''
      answerDetail.value.aiAnalysis = ''
      answerDetail.value.gradingError = ''
      regrading.value = true
      stopRegradePolling()
      regradePollTimer = window.setInterval(() => {
        pollAnswerStatus()
      }, 4000)
    } else {
      ElMessage.error(res.data?.message || '触发重判失败')
    }
  } catch (err: any) {
    console.error('触发重判失败:', err)
    ElMessage.error(err.response?.data?.message || err.response?.data || '触发重判失败')
  } finally {
    loading.value = false
  }
}

const formatDate = (dateStr: string): string => {
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

const goBack = () => {
  router.push({
    path: `/studentAnswerHistory/${studentId.value}/${courseCode.value}`,
    query: { studentName: studentName.value }
  })
}

onMounted(() => {
  studentId.value = Number(route.query.studentId)
  courseCode.value = route.query.courseCode as string
  studentName.value = route.query.studentName as string || '学生'
  
  if (studentId.value && courseCode.value) {
    loadAnswerDetail()
  } else {
    ElMessage.error('参数错误')
    router.push('/answerManagement')
  }
})

onUnmounted(() => {
  stopRegradePolling()
})
</script>

<template>
  <div class="teacher-answer-detail-container">
    <div v-loading="loading" class="answer-detail-content">
      <div v-if="answerDetail" class="answer-wrapper">
        <!-- 顶部导航栏 -->
        <div class="page-header">
          <el-button :icon="ArrowLeft" @click="goBack">返回答题历史</el-button>
          <h2 class="page-title">{{ answerDetail.assignmentTitle }}</h2>
          <div class="header-actions">
            <el-tag type="success" size="large">{{ studentName }}</el-tag>
            <el-tag v-if="answerDetail.gradingStatus" :type="answerDetail.gradingStatus === 'SUCCESS' ? 'success' : (answerDetail.gradingStatus === 'FAILED' ? 'danger' : 'info')" size="large">
              {{ answerDetail.gradingStatus === 'SUCCESS' ? '判题完成' : (answerDetail.gradingStatus === 'FAILED' ? '判题失败' : '判题中') }}
            </el-tag>
            <el-button type="warning" :loading="regrading" @click="triggerRegrade">重新AI判题</el-button>
            <el-button v-if="!editMode" type="primary" @click="toggleEditMode">修改评分</el-button>
            <template v-else>
              <el-button @click="toggleEditMode">取消</el-button>
              <el-button type="primary" @click="saveEdit">保存修改</el-button>
            </template>
          </div>
        </div>

        <el-divider />

        <!-- 学生信息 -->
        <div class="info-section">
          <div class="info-item">
            <span class="info-label">学生姓名：</span>
            <span class="info-value">{{ studentName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">提交时间：</span>
            <span class="info-value">{{ formatDate(answerDetail.submittedAt) }}</span>
          </div>
        </div>

        <el-divider />

        <!-- 题目内容区域 -->
        <div class="question-section">
          <div class="section-header">
            <h3>📝 题目内容</h3>
          </div>
          <div class="question-content markdown-body" v-html="renderMarkdown(answerDetail.assignmentContent)"></div>
        </div>

        <el-divider />

        <!-- 学生答案区域 -->
        <div class="answer-section">
          <div class="section-header">
            <h3>✍️ 学生答案</h3>
          </div>
          <div class="answer-content">{{ answerDetail.studentAnswer }}</div>
        </div>

        <el-divider />

        <!-- AI评分区域 -->
        <div class="score-section">
          <div class="section-header">
            <h3>🎯 AI评分与分析</h3>
          </div>
          
          <div v-if="!editMode" class="score-display">
            <div class="score-item">
              <span class="score-label">评分：</span>
              <el-tag type="warning" size="large">{{ answerDetail.aiScore || (answerDetail.gradingStatus === 'FAILED' ? '失败' : '批改中') }}</el-tag>
            </div>
            <div class="analysis-content">
              <span class="score-label">分析：</span>
              <div v-if="answerDetail.gradingStatus === 'FAILED'" class="analysis-box">
                {{ answerDetail.gradingError || '判题失败，请重试' }}
              </div>
              <div v-else-if="answerDetail.aiAnalysis" class="analysis-box markdown-body" v-html="renderMarkdown(answerDetail.aiAnalysis)"></div>
              <div v-else class="analysis-box">AI正在判题中，请稍后刷新。</div>
            </div>
            <div v-if="parsedEvaluation" class="analysis-content">
              <span class="score-label">结构化明细：</span>
              <div class="analysis-box">
                <div v-if="Array.isArray(parsedEvaluation.itemScores)">
                  <div v-for="(item, idx) in parsedEvaluation.itemScores" :key="`score-${idx}`">
                    第{{ item.questionNo }}题：{{ item.score }}（{{ item.comment }}）
                  </div>
                </div>
                <div v-if="Array.isArray(parsedEvaluation.weakPoints) && parsedEvaluation.weakPoints.length > 0">
                  薄弱点：{{ parsedEvaluation.weakPoints.join('；') }}
                </div>
                <div v-if="Array.isArray(parsedEvaluation.suggestions) && parsedEvaluation.suggestions.length > 0">
                  建议：{{ parsedEvaluation.suggestions.join('；') }}
                </div>
              </div>
            </div>
          </div>

          <div v-else class="score-edit">
            <div class="edit-item">
              <label class="edit-label">评分：</label>
              <el-input v-model="editScore" placeholder="例如：85/100" class="edit-input" />
            </div>
            <div class="edit-item">
              <label class="edit-label">分析：</label>
              <el-input
                v-model="editAnalysis"
                type="textarea"
                :rows="10"
                placeholder="请输入对学生答案的分析"
                class="edit-textarea"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.teacher-answer-detail-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #fafafa 0%, #f6f6f6 100%);
  padding: 20px;
}

.answer-detail-content {
  max-width: 100%;
  margin: 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  min-height: auto;
  padding: 0;
}

.answer-wrapper {
  padding: 30px;
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.page-title {
  flex: 1;
  margin: 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.info-section {
  display: flex;
  gap: 40px;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 8px;
  margin: 20px 0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-weight: 600;
  color: #606266;
}

.info-value {
  color: #303133;
}

.question-section,
.answer-section,
.score-section {
  margin: 30px 0;
}

.section-header {
  margin-bottom: 15px;
}

.section-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.question-content {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
}

.answer-content {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  min-height: 150px;
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 15px;
}

.score-display {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.score-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.score-label {
  font-weight: 600;
  color: #606266;
  font-size: 16px;
}

.analysis-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-box {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #e4e7ed;
  line-height: 1.8;
}

.score-edit {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.edit-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-label {
  font-weight: 600;
  color: #606266;
  font-size: 14px;
}

.edit-input {
  max-width: 300px;
}

.edit-textarea {
  width: 100%;
}

/* Markdown 样式 */
.markdown-body {
  font-family: inherit;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}

.markdown-body :deep(p) {
  margin: 8px 0;
  line-height: 1.8;
}

.markdown-body :deep(code) {
  background-color: rgba(175, 184, 193, 0.2);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'Courier New', monospace;
}

.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding-left: 16px;
  color: #6a737d;
  margin: 12px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border: 1px solid #dfe2e5;
  padding: 8px;
}

.markdown-body :deep(table th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.markdown-body :deep(a) {
  color: #409EFF;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(.fill-blank) {
  display: inline;
  font-weight: normal;
  font-style: normal;
  text-decoration: underline;
  text-decoration-style: solid;
  text-decoration-thickness: 1px;
}

@media (max-width: 768px) {
  .teacher-answer-detail-container {
    padding: 10px;
  }

  .answer-wrapper {
    padding: 15px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-title {
    font-size: 20px;
  }

  .info-section {
    flex-direction: column;
    gap: 10px;
  }

  .question-content,
  .answer-content {
    max-height: 300px;
  }
}
</style>
