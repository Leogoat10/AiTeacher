<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useRouter, useRoute } from 'vue-router'
import { Clock, User, School, Edit, Check } from '@element-plus/icons-vue'
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
  router.push(`/studentAssignmentDetail/${assignment.assignment_id}`)
}

// 开始答题
const startAnswer = (assignment: Assignment) => {
  router.push(`/studentAnswer/${assignment.assignment_id}`)
}



// 格式化日期
const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString('zh-CN')
}

onMounted(() => {
  loadAssignments()
  
  // 检查是否有提交成功的返回参数
  if (route.query.submitted === 'true') {
    evaluationResult.value = {
      score: route.query.score as string || '',
      analysis: route.query.analysis as string || ''
    }
    showResultDialog.value = true
    
    // 清除URL参数
    router.replace({ path: '/studentAssignments' })
  }
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

/* 填空题下划线样式 */
.markdown-body :deep(.fill-blank) {
  display: inline;
  font-weight: normal;
  font-style: normal;
  text-decoration: underline;
  text-decoration-style: solid;
  text-decoration-thickness: 1px;
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
  background: linear-gradient(135deg, #e353c6 0%, #d7040f 100%);
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

}
</style>
