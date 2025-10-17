<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { Clock, User, School, Edit, Check } from '@element-plus/icons-vue'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

const router = useRouter()

const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

// 占位符前缀，用于保护 LaTeX 公式
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()

// 提取并保护 LaTeX 公式，替换为占位符
const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  let counter = 0
  
  // 先处理块级公式 \[...\]
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: true })
    counter++
    return placeholder
  })
  
  // 再处理行内公式 \(...\)
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { formula: formula.trim(), displayMode: false })
    counter++
    return placeholder
  })
  
  return text
}

// 渲染被保护的 LaTeX 公式
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
  
  return html
}

interface Assignment {
  id: number
  assignment_id: number
  title: string
  content: string
  course_name: string
  teacher_name: string
  assignment_created_at: string
  received_at: string
  is_read: boolean
  student_answer?: string
  ai_score?: string
  ai_analysis?: string
  submitted_at?: string
}

const assignments = ref<Assignment[]>([])
const loading = ref(false)
const selectedAssignment = ref<Assignment | null>(null)
const showDetailDialog = ref(false)
const showAnswerDialog = ref(false)
const studentAnswer = ref('')
const submitting = ref(false)
const showResultDialog = ref(false)
const evaluationResult = ref<{
  score: string
  analysis: string
} | null>(null)

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

// 渲染 Markdown
const renderMarkdown = (content: string): string => {
  if (!content) return ''
  // 1. 提取并保护 LaTeX 公式
  const protectedText = protectLatexFormulas(content)
  // 2. 解析 Markdown
  const html = marked(protectedText) as string
  // 3. 渲染被保护的 LaTeX 公式
  const withLatex = renderProtectedLatex(html)
  // 4. 清理 HTML
  return DOMPurify.sanitize(withLatex)
}

// 分离题目内容和答案解析
const splitContentAndAnswer = (content: string): { question: string, answer: string } => {
  if (!content) return { question: '', answer: '' }
  
  // 尝试多种分隔符来识别答案部分
  const separators = [
    '答案与解析',
    '答案和解析', 
    '参考答案',
    '答案：',
    '答案:',
    '解析：',
    '解析:',
    '【答案】',
    '【解析】'
  ]
  
  let splitIndex = -1
  
  for (const sep of separators) {
    const index = content.indexOf(sep)
    if (index !== -1 && (splitIndex === -1 || index < splitIndex)) {
      splitIndex = index
    }
  }
  
  if (splitIndex !== -1) {
    return {
      question: content.substring(0, splitIndex).trim(),
      answer: content.substring(splitIndex).trim()
    }
  }
  
  // 如果没有找到分隔符，返回全部内容作为题目
  return { question: content, answer: '' }
}

// 检查是否已答题
const isAnswered = (assignment: Assignment): boolean => {
  return !!(assignment.student_answer || assignment.ai_score)
}

// 获取状态标签
const getStatusTag = (assignment: Assignment) => {
  if (isAnswered(assignment)) {
    return { type: 'success', text: '已答题' }
  }
  return { type: 'warning', text: '待答题' }
}

// 加载题目列表
const loadAssignments = async () => {
  loading.value = true
  try {
    const res = await apiClient.get('/student/assignments')
    if (res.data) {
      assignments.value = res.data
    }
  } catch (err: any) {
    console.error('加载题目失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/studentLogin')
    } else {
      ElMessage.error('加载题目失败')
    }
  } finally {
    loading.value = false
  }
}

// 查看题目详情
const viewDetail = (assignment: Assignment) => {
  selectedAssignment.value = assignment
  showDetailDialog.value = true
}

// 开始答题
const startAnswer = (assignment: Assignment) => {
  selectedAssignment.value = assignment
  studentAnswer.value = ''
  showDetailDialog.value = false
  showAnswerDialog.value = true
}

// 提交答案
const submitAnswer = async () => {
  if (!studentAnswer.value.trim()) {
    ElMessage.warning('请输入答案')
    return
  }

  if (!selectedAssignment.value) {
    ElMessage.error('未选择题目')
    return
  }

  try {
    await ElMessageBox.confirm(
      '提交后将无法修改，确认提交答案吗？',
      '确认提交',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    const res = await apiClient.post('/student/submitAnswer', {
      assignmentId: selectedAssignment.value.assignment_id,
      answer: studentAnswer.value
    })

    if (res.data.success) {
      ElMessage.success('答案提交成功！')
      evaluationResult.value = {
        score: res.data.score,
        analysis: res.data.analysis
      }
      showAnswerDialog.value = false
      showResultDialog.value = true
      // 重新加载题目列表
      await loadAssignments()
    } else {
      ElMessage.error(res.data.message || '提交失败')
    }
  } catch (err: any) {
    console.error('提交答案失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/studentLogin')
    } else {
      ElMessage.error(err.response?.data?.message || err.response?.data || '提交答案失败')
    }
  } finally {
    submitting.value = false
  }
}

// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAssignments()
})
</script>

<template>
  <div class="student-assignments-container">
    <div class="header">
      <h1>📚 我的题目</h1>
      <p>查看老师发送给你的题目</p>
      <el-button type="primary" @click="loadAssignments" :loading="loading">
        刷新
      </el-button>
    </div>

    <div v-loading="loading" class="assignments-list">
      <el-empty v-if="assignments.length === 0 && !loading" description="暂无题目" />

      <el-card
        v-for="assignment in assignments"
        :key="assignment.id"
        class="assignment-card"
        :class="{ unread: !assignment.is_read, answered: isAnswered(assignment) }"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <span class="title">{{ assignment.title }}</span>
            <div class="tags">
              <el-tag 
                :type="getStatusTag(assignment).type" 
                size="small"
                style="margin-left: 8px"
              >
                {{ getStatusTag(assignment).text }}
              </el-tag>
            </div>
          </div>
        </template>

        <div class="assignment-info">
          <div class="info-item">
            <el-icon><School /></el-icon>
            <span>课程：{{ assignment.course_name }}</span>
          </div>
          <div class="info-item">
            <el-icon><User /></el-icon>
            <span>老师：{{ assignment.teacher_name }}</span>
          </div>
          <div class="info-item">
            <el-icon><Clock /></el-icon>
            <span>发布时间：{{ formatDate(assignment.assignment_created_at) }}</span>
          </div>
          <div v-if="assignment.ai_score" class="info-item score-item">
            <el-icon><Check /></el-icon>
            <span class="score-text">得分：{{ assignment.ai_score }}</span>
          </div>
        </div>

<!--        <div class="assignment-preview">
          &lt;!&ndash; 预览时只显示题目部分，不显示答案 &ndash;&gt;
          <div class="content-preview" v-html="renderMarkdown(splitContentAndAnswer(assignment.content).question.substring(0, 150))"></div>
          <span v-if="splitContentAndAnswer(assignment.content).question.length > 150">...</span>
        </div>-->

        <div class="card-footer">
          <el-button type="primary" size="small" @click="viewDetail(assignment)">
            查看详情
          </el-button>
          <el-button 
            v-if="!isAnswered(assignment)"
            type="success" 
            size="small" 
            :icon="Edit" 
            @click="startAnswer(assignment)"
          >
            开始答题
          </el-button>
          <el-button 
            v-else
            type="info" 
            size="small" 
            :icon="Check" 
            disabled
          >
            已完成
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 题目详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="selectedAssignment?.title"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="selectedAssignment" class="assignment-detail">
        <div class="detail-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程">
              {{ selectedAssignment.course_name }}
            </el-descriptions-item>
            <el-descriptions-item label="老师">
              {{ selectedAssignment.teacher_name }}
            </el-descriptions-item>
            <el-descriptions-item label="发布时间">
              {{ formatDate(selectedAssignment.assignment_created_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="接收时间">
              {{ formatDate(selectedAssignment.received_at) }}
            </el-descriptions-item>
            <el-descriptions-item label="答题状态" :span="2">
              <el-tag :type="getStatusTag(selectedAssignment).type">
                {{ getStatusTag(selectedAssignment).text }}
              </el-tag>
              <span v-if="selectedAssignment.submitted_at" style="margin-left: 10px; color: #909399;">
                提交时间：{{ formatDate(selectedAssignment.submitted_at) }}
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-content">
          <h4>📝 题目内容：</h4>
          <!-- 未答题时只显示题目部分，已答题后显示完整内容 -->
          <div v-if="!isAnswered(selectedAssignment)" class="content-html markdown-body" v-html="renderMarkdown(splitContentAndAnswer(selectedAssignment.content).question)"></div>
          <div v-else class="content-html markdown-body" v-html="renderMarkdown(selectedAssignment.content)"></div>
        </div>

        <!-- 答案与解析提示（未提交时显示） -->
        <div v-if="!isAnswered(selectedAssignment) && splitContentAndAnswer(selectedAssignment.content).answer" class="answer-section-preview">
          <el-alert
            title="提示"
            type="warning"
            :closable="false"
            show-icon
          >
            <p>📌 此题目包含答案与解析，提交答案后即可查看</p>
          </el-alert>
        </div>

        <!-- 已答题内容展示 -->
        <div v-if="isAnswered(selectedAssignment)" class="answer-result-section">
          <el-divider />
          
          <div class="my-answer-section">
            <h4>✍️ 我的答案：</h4>
            <div class="my-answer-content">
              {{ selectedAssignment.student_answer }}
            </div>
          </div>

          <el-divider />

          <div class="score-analysis-section">
            <div class="score-display">
              <h4>📊 得分</h4>
              <div class="score-badge">{{ selectedAssignment.ai_score }}</div>
            </div>

            <div class="analysis-display">
              <h4>💡 AI 分析</h4>
              <div class="analysis-text markdown-body" v-html="renderMarkdown(selectedAssignment.ai_analysis || '')"></div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showDetailDialog = false">关闭</el-button>
          <el-button 
            v-if="!isAnswered(selectedAssignment!)"
            type="success" 
            :icon="Edit" 
            @click="startAnswer(selectedAssignment!)"
          >
            开始答题
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 答题对话框 -->
    <el-dialog
      v-model="showAnswerDialog"
      :title="'答题：' + selectedAssignment?.title"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="selectedAssignment" class="answer-container">
        <div class="question-section">
          <h4>📝 题目内容：</h4>
          <div class="question-content markdown-body" v-html="renderMarkdown(splitContentAndAnswer(selectedAssignment.content).question)"></div>
        </div>

        <el-divider />

        <div class="answer-section">
          <h4>✍️ 你的答案：</h4>
          <el-input
            v-model="studentAnswer"
            type="textarea"
            :rows="10"
            placeholder="请输入你的答案..."
            maxlength="5000"
            show-word-limit
          />
        </div>

        <div class="answer-tips">
          <el-alert
            title="提示"
            type="info"
            :closable="false"
          >
            <p>• 请认真作答，提交后将无法修改</p>
            <p>• AI会自动评分并给出详细的分析和建议</p>
            <p>• 建议先在本地编辑器写好答案再粘贴提交</p>
          </el-alert>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAnswerDialog = false">取消</el-button>
          <el-button
            type="primary"
            @click="submitAnswer"
            :loading="submitting"
            :disabled="!studentAnswer.trim()"
          >
            {{ submitting ? '提交中...' : '提交答案' }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 评分结果对话框 -->
    <el-dialog
      v-model="showResultDialog"
      title="📊 答题结果"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="evaluationResult" class="result-container">
        <el-result
          icon="success"
          title="答案已提交成功！"
          sub-title="AI已完成评分和分析"
        >
          <template #extra>
            <div class="result-content">
              <div class="score-section">
                <h3>📈 评分</h3>
                <div class="score-value">{{ evaluationResult.score }}</div>
              </div>

              <el-divider />

              <div class="analysis-section">
                <h3>💡 详细分析</h3>
                <div class="analysis-content markdown-body" v-html="renderMarkdown(evaluationResult.analysis)"></div>
              </div>
            </div>
          </template>
        </el-result>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="showResultDialog = false">
            知道了
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.student-assignments-container {
  max-width: 1200px;
  margin: 20px auto;
  padding: 20px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h1 {
  color: #409EFF;
  margin-bottom: 8px;
}

.header p {
  color: #666;
  margin-bottom: 15px;
}

.assignments-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 20px;
  min-height: 300px;
}

.assignment-card {
  transition: all 0.3s;
}

.assignment-card.unread {
  border-left: 4px solid #f56c6c;
}

.assignment-card.answered {
  border-left: 4px solid #67c23a;
}

.assignment-card:hover {
  transform: translateY(-5px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .tags {
  display: flex;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.assignment-info {
  margin-bottom: 15px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #606266;
  font-size: 14px;
}

.info-item .el-icon {
  color: #409EFF;
}

.score-item {
  color: #67c23a;
  font-weight: bold;
}

.score-text {
  color: #67c23a;
  font-weight: bold;
  font-size: 15px;
}

/*.assignment-preview {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 15px;
  min-height: 80px;
}*/

.content-preview {
  color: #606266;
  line-height: 1.6;
}

.content-preview :deep(p) {
  margin: 8px 0;
}

.content-preview :deep(code) {
  background-color: #e8e8e8;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.assignment-detail {
  padding: 10px 0;
}

.detail-info {
  margin-bottom: 20px;
}

.detail-content {
  margin-top: 20px;
}

.detail-content h4 {
  margin-bottom: 10px;
  color: #303133;
}

.answer-section-preview {
  margin-top: 20px;
}

.answer-section-preview :deep(.el-alert) {
  border-radius: 8px;
}

.answer-section-preview :deep(.el-alert__description) p {
  margin: 0;
  font-size: 14px;
}

.content-html {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  line-height: 1.8;
  max-height: 500px;
  overflow-y: auto;
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

/* 已答题内容展示 */
.answer-result-section {
  margin-top: 20px;
}

.my-answer-section {
  margin: 20px 0;
}

.my-answer-section h4 {
  color: #303133;
  margin-bottom: 12px;
}

.my-answer-content {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 8px;
  border-left: 4px solid #909399;
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
}

.score-analysis-section {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 20px;
  margin-top: 20px;
}

.score-display {
  text-align: center;
}

.score-display h4 {
  color: #303133;
  margin-bottom: 15px;
}

.score-badge {
  background: linear-gradient(135deg, #e353c6 0%, #d7040f 100%);
  color: white;
  font-size: 32px;
  font-weight: bold;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.analysis-display h4 {
  color: #303133;
  margin-bottom: 12px;
}

.analysis-text {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
  line-height: 1.8;
  color: #606266;
}

.content-text {
  white-space: pre-wrap;
  word-wrap: break-word;
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  line-height: 1.8;
  font-family: inherit;
  margin: 0;
  max-height: 500px;
  overflow-y: auto;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 答题对话框样式 */
.answer-container {
  padding: 10px 0;
}

.question-section,
.answer-section {
  margin-bottom: 20px;
}

.question-section h4,
.answer-section h4 {
  color: #303133;
  margin-bottom: 12px;
  font-size: 16px;
}

.question-content {
  background-color: #f5f7fa;
  border-radius: 8px;
  padding: 15px;
  max-height: 300px;
  overflow-y: auto;
}

.answer-tips {
  margin-top: 15px;
}

.answer-tips :deep(.el-alert__description) p {
  margin: 5px 0;
  font-size: 13px;
}

/* 评分结果样式 */
.result-container {
  padding: 10px;
}

.result-content {
  width: 100%;
  text-align: left;
  padding: 20px;
}

.score-section {
  text-align: center;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  margin-bottom: 20px;
}

.score-section h3 {
  margin: 0 0 15px 0;
  font-size: 18px;
  font-weight: normal;
  opacity: 0.95;
}

.score-value {
  font-size: 36px;
  font-weight: bold;
  letter-spacing: 2px;
}

.analysis-section h3 {
  color: #303133;
  margin-bottom: 15px;
  font-size: 18px;
}

.analysis-content {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
  line-height: 1.8;
  color: #606266;
}

@media (max-width: 768px) {
  .assignments-list {
    grid-template-columns: 1fr;
  }
  
  .el-dialog {
    width: 95% !important;
  }

  .score-analysis-section {
    grid-template-columns: 1fr;
  }

  .score-badge {
    padding: 20px;
    font-size: 28px;
  }
}
</style>
