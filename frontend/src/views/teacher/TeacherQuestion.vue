<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import { useRouter } from "vue-router";
import { Clock, ChatDotRound, Loading } from '@element-plus/icons-vue'
import 'katex/dist/katex.min.css'

const router = useRouter()

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
      // 替换所有出现的占位符（可能被 HTML 编码或包装在标签中）
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

// 将 axios 实例改为启用 withCredentials，确保浏览器携带 session cookie
const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true
})

// 添加历史对话相关变量
const showHistoryDialog = ref(false)
const historyConversations = ref<Array<{id: number, createTime: string,title: string}>>([])
const loadingHistory = ref(false)
const deletingConversationId = ref<number | null>(null)


const deleteConversation = async (conversationId: number) => {
  if (!await confirmDelete()) return

  deletingConversationId.value = conversationId
  try {
    const res = await apiClient.post('/teacher/deleteConversation', {
      conversationId: conversationId
    })

    if (res.data.success) {
      ElMessage.success('删除成功')

      // 如果删除的是当前对话，切换到新对话
      if (conversationId === currentConversationId.value) {
        currentConversationId.value = null
        chatHistory.value = []
        selectedMessages.value.clear()
        messageIdCounter = 0
      }

      // 从历史列表中移除
      historyConversations.value = historyConversations.value.filter(
          conv => conv.id !== conversationId
      )
    } else {
      ElMessage.error('删除失败: ' + (res.data.error || '未知错误'))
    }
  } catch (err: any) {
    console.error('删除对话错误:', err)
    ElMessage.error('删除失败: ' + (err.response?.data?.error || '未知错误'))
  } finally {
    deletingConversationId.value = null
  }
}

const confirmDelete = async () => {
  return new Promise((resolve) => {
    import('element-plus').then(({ ElMessageBox }) => {
      ElMessageBox.confirm(
          '确定要删除这个对话吗？此操作无法撤销。',
          '确认删除',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          }
      ).then(() => {
        resolve(true)
      }).catch(() => {
        resolve(false)
      })
    })
  })
}


// 获取历史对话列表
const fetchHistoryConversations = async () => {
  loadingHistory.value = true
  try {
    const res = await apiClient.get('/teacher/conversations')
    if (res.data.success) {
      historyConversations.value = res.data.conversations || []
    } else {
      ElMessage.error('获取历史对话失败: ' + (res.data.error || '未知错误'))
    }
  } catch (err: any) {
    console.error('获取历史对话错误:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/')
    } else {
      ElMessage.error('获取历史对话失败: ' + (err.response?.data?.error || '未知错误'))
    }
  } finally {
    loadingHistory.value = false
  }
}

// 打开历史对话弹窗
const openHistoryDialog = async () => {
  showHistoryDialog.value = true
  await fetchHistoryConversations()
}

// 切换到指定对话
const switchToConversation = async (conversationId: number) => {
  try {
    const res = await apiClient.get(`/teacher/conversation/${conversationId}`)
    if (res.data.success) {
      currentConversationId.value = conversationId
      // 清空当前聊天记录
      chatHistory.value = []
      selectedMessages.value = new Set()
      messageIdCounter = 0

      // 加载对话历史消息
      const messages = res.data.messages || []
        for (const msg of messages) {
          let content = msg.content
          if (msg.role === 'ai') {
            // 1. 提取并保护 LaTeX 公式
            const protectedText = protectLatexFormulas(msg.content)
            // 2. 解析 Markdown
            const html = await marked.parse(protectedText)
            // 3. 渲染被保护的 LaTeX 公式
            const withLatex = renderProtectedLatex(html)
            // 4. 清理 HTML
            content = DOMPurify.sanitize(withLatex)
          }

          chatHistory.value.push({
            role: msg.role,
            content,
            rawContent: msg.role === 'ai' ? msg.content : undefined,
            renderedQuestionContent: msg.role === 'ai' ? splitRenderedContent(content).questionHtml : undefined,
            renderedSolutionContent: msg.role === 'ai' ? splitRenderedContent(content).solutionHtml : undefined,
            timestamp: new Date(msg.timestamp),
            id: messageIdCounter++
          })
        }


      ElMessage.success('切换对话成功')
      showHistoryDialog.value = false
    } else {
      ElMessage.error('加载对话失败: ' + (res.data.error || '未知错误'))
    }
  } catch (err: any) {
    console.error('加载对话错误:', err)
    ElMessage.error('加载对话失败: ' + (err.response?.data?.error || '未知错误'))
  }
}

// 添加响应式变量
const creatingConversation = ref(false)

// 添加当前会话ID的响应式变量
const currentConversationId = ref<number | null>(null)

// 添加新建对话方法
const createNewConversation = async () => {
  creatingConversation.value = true
  try {
    const res = await apiClient.post('/teacher/newConversation')

    if (res.data.success) {
      ElMessage.success('成功创建新对话')
      currentConversationId.value = res.data.conversationId
      // 清空聊天历史记录
      chatHistory.value = []
      selectedMessages.value = new Set()
      messageIdCounter = 0
    } else {
      ElMessage.error('创建对话失败: ' + (res.data.error || '未知错误'))
    }
  } catch (err: any) {
    console.error('创建对话错误:', err)
    if (err.response) {
      if (err.response.status === 401) {
        ElMessage.error('未登录或会话已过期，请先登录')
        router.push('/')
      } else {
        ElMessage.error('创建对话失败: ' + (err.response.data?.error || '未知错误'))
      }
    } else {
      ElMessage.error('网络连接失败，请检查服务是否启动')
    }
  } finally {
    creatingConversation.value = false
  }
}

// 修改聊天历史记录的类型定义，增加rawContent字段
const chatHistory = ref<Array<{
  role: string;
  content: string;
  rawContent?: string;  // 添加原始内容字段
  renderedQuestionContent?: string;
  renderedSolutionContent?: string;
  structuredQuestions?: Array<{
    stem: string;
    type: string;
    options: string[];
    answer: string;
    analysis?: string;
    score?: number;
    difficulty?: string;
  }>;
  qualityIssues?: Array<{
    severity: string;
    questionIndex: number;
    message: string;
  }>;
  contextUsed?: boolean;
  contextRounds?: number;
  timestamp: Date;
  id: number
}>>([])

const loading = ref(false)
const taskStatusText = ref('')
const taskStatus = ref<'IDLE' | 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'COMPLETED_WITH_WARNINGS'>('IDLE')
const taskProgress = ref(0)
const taskErrorMessage = ref('')
const currentTaskId = ref<number | null>(null)
const lastRequestPayload = ref<any>(null)
const selectedMessages = ref<Set<number>>(new Set())
let messageIdCounter = 0

// 表单数据
const subject = ref('')
const grade = ref('')
const difficulty = ref('')
const questionType = ref('')
const questionCount = ref('')
const customMessage = ref('')
const contextInstruction = ref('')
const useRecentContext = ref(false)
const CONTEXT_ROUNDS = 5
const educationOptions = [
  {
    value: '小学',
    label: '小学',
    children: [
      { value: '一年级', label: '一年级' },
      { value: '二年级', label: '二年级' },
      { value: '三年级', label: '三年级' },
      { value: '三年级', label: '四年级' },
      { value: '五年级', label: '五年级' },
      { value: '六年级', label: '六年级' }
    ]
  },
  {
    value: '初中',
    label: '初中',
    children: [
      { value: '七年级', label: '七年级' },
      { value: '八年级', label: '八年级' },
      { value: '九年级', label: '九年级' }
    ]
  },
  {
    value: '高中',
    label: '高中',
    children: [
      { value: '高一', label: '高一' },
      { value: '高二', label: '高二' },
      { value: '高三', label: '高三' }
    ]
  },
  {
    value: '大学',
    label: '大学',
    children: [
      { value: '大学一年级', label: '大学一年级' },
      { value: '大学二年级', label: '大学二年级' },
      { value: '大学三年级', label: '大学三年级' },
      { value: '大学四年级', label: '大学四年级' }
    ]
  },
  {
    value: '其他',
    label: '其他'
  }
]

const statusProgressMap: Record<string, number> = {
  PENDING: 15,
  RUNNING: 60,
  SUCCESS: 100,
  COMPLETED_WITH_WARNINGS: 100,
  FAILED: 100
}

const statusTypeMap: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCESS: 'success',
  COMPLETED_WITH_WARNINGS: 'warning',
  FAILED: 'danger'
}

const statusLabelMap: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '生成中',
  SUCCESS: '生成成功',
  COMPLETED_WITH_WARNINGS: '生成成功（需复核）',
  FAILED: '生成失败'
}

const taskStatusTagType = computed(() => statusTypeMap[taskStatus.value] || 'info')
const taskStatusLabel = computed(() => statusLabelMap[taskStatus.value] || '等待中')
const hasContextHistory = computed(() => chatHistory.value.some(msg => msg.role === 'ai' && !!(msg.rawContent || msg.content)))
const contextModeAvailable = computed(() => !!currentConversationId.value && hasContextHistory.value)
const canSubmit = computed(() => {
  if (loading.value) return false
  if (useRecentContext.value) {
    return contextModeAvailable.value && !!contextInstruction.value.trim()
  }
  return !!subject.value.trim() && !!grade.value && !!difficulty.value && !!questionType.value && !!questionCount.value
})
const taskContextText = computed(() => {
  const useContext = lastRequestPayload.value?.useContext
  const rounds = lastRequestPayload.value?.contextRounds || CONTEXT_ROUNDS
  if (useContext === undefined) {
    return useRecentContext.value ? `本次任务将关联最近${CONTEXT_ROUNDS}轮上下文` : '本次任务不关联历史上下文'
  }
  return useContext ? `本次任务将关联最近${rounds}轮上下文` : '本次任务不关联历史上下文'
})

watch(contextModeAvailable, (available) => {
  if (!available && useRecentContext.value) {
    useRecentContext.value = false
  }
})

const buildMarkdownFromResult = (questions: any[], issues: any[]) => {
  const markdownParts: string[] = []
  questions.forEach((q: any, idx: number) => {
    markdownParts.push(`${idx + 1}. ${q.stem || ''}`)
    if (Array.isArray(q.options) && q.options.length > 0) {
      q.options.forEach((opt: string) => markdownParts.push(`   ${opt}`))
    }
    markdownParts.push('   答案与解析：')
    markdownParts.push(`   答案：${q.answer || ''}`)
    if (q.analysis) markdownParts.push(`   解析：${q.analysis}`)
    markdownParts.push('')
  })

  if (Array.isArray(issues) && issues.length > 0) {
    markdownParts.push('---')
    markdownParts.push('质量提示：')
    issues.forEach((it: any) => {
      markdownParts.push(`- [${it.severity}] 第${it.questionIndex}题：${it.message}`)
    })
  }
  return markdownParts.join('\n').trim()
}

const splitRenderedContent = (htmlContent: string): { questionHtml: string; solutionHtml: string } => {
  if (!htmlContent) return { questionHtml: '', solutionHtml: '' }

  const markers = ['答案与解析', '答案和解析', '参考答案', '答案：', '答案:', '【答案】', '【解析】']
  let splitIndex = -1

  for (const marker of markers) {
    const idx = htmlContent.indexOf(marker)
    if (idx !== -1 && (splitIndex === -1 || idx < splitIndex)) {
      splitIndex = idx
    }
  }

  if (splitIndex === -1) {
    return { questionHtml: htmlContent, solutionHtml: '' }
  }

  const paragraphStart = htmlContent.lastIndexOf('<p', splitIndex)
  const safeIndex = paragraphStart !== -1 ? paragraphStart : splitIndex
  return {
    questionHtml: htmlContent.substring(0, safeIndex),
    solutionHtml: htmlContent.substring(safeIndex)
  }
}

const submitGenerationTask = async (payload: any, pushRetryUserMessage = false) => {
  if (pushRetryUserMessage) {
    chatHistory.value.push({
      role: 'user',
      content: '按相同参数重试生成',
      timestamp: new Date(),
      id: messageIdCounter++
    })
  }

  loading.value = true
  taskStatus.value = 'PENDING'
  taskProgress.value = 10
  taskErrorMessage.value = ''
  taskStatusText.value = '任务已提交，等待AI生成...'

  try {
    const taskRes = await apiClient.post('/teacher/question/v2/tasks', payload)

    if (!taskRes.data.success) {
      throw new Error(taskRes.data.error || '任务创建失败')
    }

    const taskUseContext = taskRes.data.useContext ?? payload.useContext ?? true
    const taskContextRounds = taskRes.data.contextRounds ?? payload.contextRounds ?? CONTEXT_ROUNDS

    if (taskRes.data.newConversationId !== undefined && taskRes.data.newConversationId !== null) {
      currentConversationId.value = taskRes.data.newConversationId
    } else if (taskRes.data.conversationId !== undefined && taskRes.data.conversationId !== null) {
      currentConversationId.value = taskRes.data.conversationId
    }

    const taskId = taskRes.data.taskId
    currentTaskId.value = taskId
    const maxAttempts = 60
    let attempt = 0
    let finalStatusRes: any = null

    while (attempt < maxAttempts) {
      attempt++
      await new Promise(resolve => setTimeout(resolve, 1000))
      const statusRes = await apiClient.get(`/teacher/question/v2/tasks/${taskId}`)
      if (!statusRes.data.success) {
        throw new Error(statusRes.data.error || '查询任务状态失败')
      }

      const status = statusRes.data.status
      taskStatus.value = status
      taskProgress.value = statusProgressMap[status] ?? 40
      taskStatusText.value = `任务状态：${statusLabelMap[status] || status}`

      if (status === 'SUCCESS' || status === 'COMPLETED_WITH_WARNINGS' || status === 'FAILED') {
        finalStatusRes = statusRes.data
        break
      }
    }

    if (!finalStatusRes) {
      throw new Error('任务超时，请稍后在历史对话中查看结果')
    }

    if (finalStatusRes.status === 'FAILED') {
      taskErrorMessage.value = finalStatusRes.errorMessage || '未知错误'
      throw new Error(taskErrorMessage.value)
    }

    const result = finalStatusRes.result || {}
    const questions = Array.isArray(result.questions) ? result.questions : []
    const issues = Array.isArray(result.issues) ? result.issues : []
    const markdownContent = buildMarkdownFromResult(questions, issues)

    const protectedText = protectLatexFormulas(markdownContent)
    const html = await marked.parse(protectedText)
    const withLatex = renderProtectedLatex(html)
    const htmlContent = DOMPurify.sanitize(withLatex)

    chatHistory.value.push({
      role: 'ai',
      content: htmlContent,
      rawContent: markdownContent,
      renderedQuestionContent: splitRenderedContent(htmlContent).questionHtml,
      renderedSolutionContent: splitRenderedContent(htmlContent).solutionHtml,
      structuredQuestions: questions.map((q: any) => ({
        stem: q.stem || '',
        type: q.type || '',
        options: Array.isArray(q.options) ? q.options : [],
        answer: q.answer || '',
        analysis: q.analysis || '',
        score: q.score,
        difficulty: q.difficulty
      })),
      qualityIssues: issues,
      contextUsed: !!taskUseContext,
      contextRounds: Number(taskContextRounds) || CONTEXT_ROUNDS,
      timestamp: new Date(),
      id: messageIdCounter++
    })

    if (finalStatusRes.status === 'COMPLETED_WITH_WARNINGS') {
      ElMessage.warning('题目已生成，但存在质量警告，请人工复核')
    } else {
      ElMessage.success('题目生成完成')
    }
  } catch (err: any) {
    console.error('任务处理错误:', err)
    taskStatus.value = 'FAILED'
    taskProgress.value = 100
    taskErrorMessage.value = err?.message || '未知错误'

    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/')
    } else {
      ElMessage.error('生成失败: ' + taskErrorMessage.value)
    }

    chatHistory.value.push({
      role: 'ai',
      content: `生成失败：${taskErrorMessage.value}`,
      timestamp: new Date(),
      id: messageIdCounter++
    })
  } finally {
    loading.value = false
    taskStatusText.value = ''
  }
}

const retryLastTask = async () => {
  if (!lastRequestPayload.value) {
    ElMessage.warning('没有可重试的任务参数')
    return
  }
  await submitGenerationTask(lastRequestPayload.value, true)
}

const send = async () => {
  if (useRecentContext.value) {
    if (!contextModeAvailable.value) {
      ElMessage.warning('当前会话暂无历史对话，无法使用参考上下文模式')
      return
    }
    if (!contextInstruction.value.trim()) {
      ElMessage.warning('请输入上下文增量指令')
      return
    }
  } else {
    if (!subject.value.trim()) {
      ElMessage.warning('请输入科目/专业')
      return
    }
    if (!grade.value) {
      ElMessage.warning('请选择年级')
      return
    }
    if (!difficulty.value) {
      ElMessage.warning('请选择难易程度')
      return
    }
    if (!questionType.value) {
      ElMessage.warning('请选择题型')
      return
    }
    if (!questionCount.value) {
      ElMessage.warning('请输入题目数量')
      return
    }
    if (isNaN(Number(questionCount.value)) || Number(questionCount.value) <= 0) {
      ElMessage.warning('题目数必须为大于0的数字')
      return
    }
  }

  let userDisplayMessage = ''
  if (useRecentContext.value) {
    userDisplayMessage = `上下文增量指令：${contextInstruction.value.trim()} [关联最近${CONTEXT_ROUNDS}轮上下文]`
  } else {
    userDisplayMessage = `${subject.value} ${grade.value} ${difficulty.value} ${questionType.value}`
    if (questionCount.value) userDisplayMessage += ` (${questionCount.value}题)`
    if (customMessage.value.trim()) userDisplayMessage += ` - ${customMessage.value}`
    userDisplayMessage += ' [不关联历史上下文]'
  }

  chatHistory.value.push({
    role: 'user',
    content: userDisplayMessage,
    timestamp: new Date(),
    id: messageIdCounter++
  })

  const payload = {
    subject: useRecentContext.value ? null : subject.value,
    grade: useRecentContext.value ? null : grade.value.toString(),
    difficulty: useRecentContext.value ? null : difficulty.value,
    questionType: useRecentContext.value ? null : questionType.value,
    questionCount: useRecentContext.value ? null : questionCount.value,
    customMessage: useRecentContext.value ? contextInstruction.value.trim() : (customMessage.value || null),
    conversationId: currentConversationId.value,
    useContext: useRecentContext.value,
    contextRounds: CONTEXT_ROUNDS
  }
  lastRequestPayload.value = payload
  await submitGenerationTask(payload, false)
  if (useRecentContext.value) {
    contextInstruction.value = ''
  }
}

// 添加清空表单的方法
const clearForm = () => {
  subject.value = ''
  grade.value = ''
  difficulty.value = ''
  questionType.value = ''
  questionCount.value = ''
  customMessage.value = ''
  contextInstruction.value = ''
  useRecentContext.value = false
}

// 切换消息选择状态
const toggleMessageSelection = (id: number) => {
  if (selectedMessages.value.has(id)) {
    selectedMessages.value.delete(id)
  } else {
    selectedMessages.value.add(id)
  }
  selectedMessages.value = new Set(selectedMessages.value)
}

// 全选/取消全选
const toggleSelectAll = () => {
  const aiMessages = chatHistory.value.filter(msg => msg.role === 'ai')
  const allSelected = aiMessages.every(msg => selectedMessages.value.has(msg.id))

  if (allSelected) {
    aiMessages.forEach(msg => selectedMessages.value.delete(msg.id))
  } else {
    aiMessages.forEach(msg => selectedMessages.value.add(msg.id))
  }
  selectedMessages.value = new Set(selectedMessages.value)
}

// 检查是否所有AI消息都被选中
const isAllSelected = computed(() => {
  const aiMessages = chatHistory.value.filter(msg => msg.role === 'ai')
  return aiMessages.length > 0 && aiMessages.every(msg => selectedMessages.value.has(msg.id))
})

// 检查是否有选中的消息
const hasSelectedMessages = computed(() => {
  return selectedMessages.value.size > 0
})

// 导出为Word文档 - 修改导出逻辑
const exportToWord = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.warning('暂无内容可导出')
    return
  }

  // 如果没有选中任何消息，则导出所有AI生成的内容
  let contentsToExport: string[]

  if (selectedMessages.value.size === 0) {
    contentsToExport = chatHistory.value
        .filter(msg => msg.role === 'ai')
        .map(msg => msg.rawContent || msg.content)  // 优先使用原始内容
  } else {
    // 只导出选中的内容
    contentsToExport = chatHistory.value
        .filter(msg => msg.role === 'ai' && selectedMessages.value.has(msg.id))
        .map(msg => msg.rawContent || msg.content)  // 优先使用原始内容
  }

  if (contentsToExport.length === 0) {
    ElMessage.warning('没有AI生成的内容可导出')
    return
  }

  const content = contentsToExport.join('\n\n')
  createAndDownloadWord(content)
}

// 创建并下载Word文档
const createAndDownloadWord = (content: string) => {
  const blob = new Blob([content], { type: 'application/msword;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `AI教师助手_${new Date().toISOString().slice(0, 10)}.doc`
  link.click()

  ElMessage.success('导出成功')
}

// 发送题目相关
const showSendDialog = ref(false)
const teacherCourses = ref<Array<{courseCode: string, courseName: string}>>([])
const sendForm = ref({
  courseCode: '',
  title: ''
})
const sendingAssignment = ref(false)

// 获取教师课程列表
const fetchTeacherCourses = async () => {
  try {
    const res = await apiClient.get('/course/teacherCourse')
    if (res.data) {
      teacherCourses.value = res.data
    }
  } catch (err: any) {
    console.error('获取课程列表失败:', err)
    ElMessage.error('获取课程列表失败')
  }
}

// 打开发送题目对话框
const openSendDialog = async () => {
  // 检查是否有选中的消息
  if (selectedMessages.value.size === 0) {
    ElMessage.warning('请先选择要发送的题目')
    return
  }

  sendForm.value.courseCode = ''
  sendForm.value.title = ''
  await fetchTeacherCourses()
  showSendDialog.value = true
}

// 发送题目到课程 - 发送所有选中的题目
const sendAssignmentToCourse = async () => {
  if (!sendForm.value.courseCode) {
    ElMessage.warning('请选择课程')
    return
  }
  if (!sendForm.value.title.trim()) {
    ElMessage.warning('请输入题目标题')
    return
  }

  // 获取选中的AI消息
  const selectedAIMessages = chatHistory.value.filter(
    msg => msg.role === 'ai' && selectedMessages.value.has(msg.id)
  )

  if (selectedAIMessages.length === 0) {
    ElMessage.warning('没有选中的题目可发送')
    return
  }

  sendingAssignment.value = true
  let successCount = 0
  let failCount = 0
  let totalStudents = 0

  try {
    // 为每个选中的消息发送题目
    for (let i = 0; i < selectedAIMessages.length; i++) {
      const msg = selectedAIMessages[i]
      try {
        // 使用rawContent（Markdown原文）而不是HTML内容
        const content = msg.rawContent || msg.content
        const titleWithIndex = selectedAIMessages.length > 1 
          ? `${sendForm.value.title} (${i + 1})`
          : sendForm.value.title

        const res = await apiClient.post('/assignment/sendToCourse', {
          content: content,
          courseCode: sendForm.value.courseCode,
          title: titleWithIndex
        })

        if (res.data.success) {
          successCount++
          totalStudents = res.data.sentCount || 0
        } else {
          failCount++
        }
      } catch (err) {
        failCount++
        console.error('发送单个题目失败:', err)
      }
    }

    if (successCount > 0) {
      ElMessage.success(`成功发送 ${successCount} 个题目给 ${totalStudents} 名学生！`)
      showSendDialog.value = false
      // 清空选择
      selectedMessages.value.clear()
    }
    
    if (failCount > 0) {
      ElMessage.warning(`有 ${failCount} 个题目发送失败`)
    }

  } catch (err: any) {
    console.error('发送题目失败:', err)
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/')
    } else {
      ElMessage.error('发送失败: ' + (err.response?.data?.message || '未知错误'))
    }
  } finally {
    sendingAssignment.value = false
  }
}
</script>

<template>
  <div class="ai-teacher-container">
    <div class="header">
      <h1>👩‍🏫 AI 教师助手</h1>
      <p>向 AI 提问，获取教学方案建议</p>
      <div class="header-buttons">
        <el-button
            size="small"
            @click="openHistoryDialog"
            type="primary"
        >
          <el-icon><Clock /></el-icon>
          历史对话
        </el-button>
        <el-button
          size="small"
          @click="toggleSelectAll"
          :type="isAllSelected ? 'danger' : 'primary'"
          :disabled="chatHistory.filter(msg => msg.role === 'ai').length === 0"
        >
          {{ isAllSelected ? '取消全选' : '全选' }}
        </el-button>
        <el-button
          size="small"
          @click="openSendDialog"
          type="warning"
          :disabled="!hasSelectedMessages"
        >
          📤 发送给学生
        </el-button>
        <el-button
          size="small"
          @click="exportToWord"
          type="success"
          :disabled="!hasSelectedMessages && chatHistory.filter(msg => msg.role === 'ai').length === 0"
        >
          导出为Word
        </el-button>

      </div>
    </div>
    <div class="main-container">
      <!-- 左侧表单区域 -->
      <div class="form-container">
        <div class="form-title-container">
          <div class="form-title">题目生成设置</div>
          <div class="form-title-actions">
            <el-button size="small" @click="createNewConversation" :loading="creatingConversation">
              <el-icon><ChatDotRound /></el-icon>
              新建对话
            </el-button>
            <el-button size="small" @click="clearForm" type="warning">
              <el-icon><i class="fas fa-trash-alt"></i></el-icon>
              清空表单
            </el-button>
          </div>
        </div>
        <el-form label-position="top">
          <el-form-item label="上下文关联">
            <div class="context-settings">
              <el-switch
                v-model="useRecentContext"
                :disabled="loading || !contextModeAvailable"
                active-text="关联最近上下文"
                inactive-text="不关联历史上下文"
              />
              <el-tag v-if="useRecentContext" size="small" type="info">最近{{ CONTEXT_ROUNDS }}轮</el-tag>
            </div>
            <div class="context-settings-tip" v-if="!contextModeAvailable">
              当前会话暂无历史对话，暂不可启用参考上下文模式
            </div>
          </el-form-item>

          <template v-if="!useRecentContext">
            <el-form-item label="科目/专业">
              <el-input
                v-model="subject"
                placeholder="请输入科目/专业"
                :disabled="loading"
              />
            </el-form-item>

            <el-form-item label="年级">
              <el-cascader
                  v-model="grade"
                  :options="educationOptions"
                  :props="{ expandTrigger: 'hover' }"
                  placeholder="请选择年级"
                  style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="难易程度">
              <el-select
                v-model="difficulty"
                placeholder="请选择难易程度"
                :disabled="loading"
                style="width: 100%"
              >
                <el-option label="简单" value="简单" />
                <el-option label="中等" value="中等" />
                <el-option label="困难" value="困难" />
              </el-select>
            </el-form-item>

            <el-form-item label="题型">
              <el-select
                v-model="questionType"
                placeholder="请选择题型"
                :disabled="loading"
                style="width: 100%"
              >
                <el-option label="选择题" value="选择题" />
                <el-option label="填空题" value="填空题" />
                <el-option label="判断题" value="判断题" />
                <el-option label="简答题" value="简答题" />
                <el-option label="解答题" value="解答题" />
              </el-select>
            </el-form-item>

            <el-form-item label="题目数">
              <el-input
                  v-model="questionCount"
                  placeholder="请输入题目数量"
                  :disabled="loading"
                  type="number"
                  min="1"
                  required
              />
            </el-form-item>

            <el-form-item label="具体要求（选填）">
              <el-input
                v-model="customMessage"
                placeholder="请输入具体要求"
                :disabled="loading"
                type="textarea"
                :rows="3"
              />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="上下文增量指令">
              <el-input
                v-model="contextInstruction"
                placeholder="例如：保持题型和题量不变，再难一些；或者改成应用题"
                :disabled="loading"
                type="textarea"
                :rows="4"
              />
            </el-form-item>
          </template>

          <el-button
              type="primary"
              @click="send"
              :loading="loading"
              :disabled="!canSubmit"    style="width: 100%"
          >
            生成题目
          </el-button>

        </el-form>
      </div>

      <!-- 右侧AI对话区域 -->
      <div class="chat-container">
        <div v-if="loading || taskStatus === 'FAILED'" class="task-panel">
          <div class="task-panel-header">
            <span>生成任务</span>
            <el-tag :type="taskStatusTagType" size="small">{{ taskStatusLabel }}</el-tag>
          </div>
          <el-progress :percentage="taskProgress" :status="taskStatus === 'FAILED' ? 'exception' : undefined" />
          <div class="task-panel-text">
            <span v-if="taskStatusText">{{ taskStatusText }}</span>
            <span v-else-if="taskStatus === 'FAILED'">失败原因：{{ taskErrorMessage || '未知错误' }}</span>
            <div class="task-context-text">
              {{ taskContextText }}
            </div>
          </div>
          <div class="task-panel-actions" v-if="taskStatus === 'FAILED' && !loading">
            <el-button size="small" type="danger" plain @click="retryLastTask">重试本次参数</el-button>
          </div>
        </div>

        <div
          v-for="(chat) in chatHistory"
          :key="chat.id"
          class="message"
          :class="chat.role"
        >
          <div class="message-header">
            <strong>{{ chat.role === 'user' ? '👤 您' : '🤖 AI助手' }}</strong>
            <div class="message-actions">
              <el-checkbox
                v-if="chat.role === 'ai'"
                :model-value="selectedMessages.has(chat.id)"
                @change="() => toggleMessageSelection(chat.id)"
                size="small"
              />
              <span class="timestamp">{{ chat.timestamp.toLocaleTimeString() }}</span>
            </div>
          </div>
          <el-card
            :shadow="chat.role === 'user' ? 'never' : 'hover'"
            :class="['message-content', chat.role]"
          >
            <template v-if="chat.role === 'ai'">
              <div class="context-hint" v-if="chat.contextUsed !== undefined">
                {{ chat.contextUsed ? `本次已关联最近${chat.contextRounds || CONTEXT_ROUNDS}轮上下文` : '本次未关联历史上下文（纯参数生成）' }}
              </div>
              <div v-if="chat.structuredQuestions && chat.structuredQuestions.length > 0" class="question-cards">
                <div
                  v-for="(q, idx) in chat.structuredQuestions"
                  :key="`${chat.id}-${idx}`"
                  class="question-card"
                >
                  <div class="question-card-title">
                    <strong>第{{ idx + 1 }}题</strong>
                    <div class="question-card-tags">
                      <el-tag size="small" type="info">{{ q.type || '未分类' }}</el-tag>
                      <el-tag size="small" type="success" v-if="q.difficulty">{{ q.difficulty }}</el-tag>
                      <el-tag size="small" type="warning" v-if="q.score">分值 {{ q.score }}</el-tag>
                    </div>
                  </div>

                  <div class="question-part-title">题目</div>
                  <div class="question-card-stem">{{ q.stem }}</div>
                  <div class="question-card-options" v-if="q.options && q.options.length > 0">
                    <div v-for="(opt, optIdx) in q.options" :key="`${chat.id}-${idx}-opt-${optIdx}`">{{ opt }}</div>
                  </div>

                  <div class="question-split-line"></div>

                  <div class="question-part-title solution">答案与解析</div>
                  <div class="question-card-answer">答案：{{ q.answer }}</div>
                  <div class="question-card-analysis" v-if="q.analysis">解析：{{ q.analysis }}</div>
                  <div class="question-card-analysis" v-else>解析：暂无</div>
                  </div>

                <div v-if="chat.qualityIssues && chat.qualityIssues.length > 0" class="quality-issues">
                  <div class="quality-issues-title">质量提示</div>
                  <div
                    class="quality-issue-item"
                    v-for="(issue, issueIdx) in chat.qualityIssues"
                    :key="`${chat.id}-issue-${issueIdx}`"
                  >
                    [{{ issue.severity }}] 第{{ issue.questionIndex }}题：{{ issue.message }}
                  </div>
                </div>
              </div>
              <div v-else>
                <div class="question-part-title">题目</div>
                <div class="markdown-body" v-html="chat.renderedQuestionContent || chat.content"></div>
                <template v-if="chat.renderedSolutionContent">
                  <div class="question-split-line"></div>
                  <div class="question-part-title solution">答案与解析</div>
                  <div class="markdown-body" v-html="chat.renderedSolutionContent"></div>
                </template>
              </div>
            </template>
            <pre v-else>{{ chat.content }}</pre>
          </el-card>
        </div>

        <div v-if="loading" class="loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI正在思考中...</span>
          <div v-if="taskStatusText" class="task-status">{{ taskStatusText }}</div>
        </div>
      </div>
    </div>
  </div>

  <!-- 历史对话弹窗 -->
  <!-- TeacherQuestion.vue 中的历史对话弹窗部分 -->
  <el-dialog
      v-model="showHistoryDialog"
      title="历史对话"
      width="500px"
      :close-on-click-modal="false"
  >
    <div class="history-dialog-content">
      <el-empty v-if="historyConversations.length === 0 && !loadingHistory" description="暂无历史对话" />

      <div v-else class="history-list">
        <el-scrollbar max-height="400px">
          <div
              v-for="conversation in historyConversations"
              :key="conversation.id"
              class="history-item"
              :class="{ active: conversation.id === currentConversationId }"
          >
            <div
                class="history-item-content"
                @click="switchToConversation(conversation.id)"
            >
              <el-icon class="history-icon"><ChatDotRound /></el-icon>
              <div class="history-info">
                <div class="history-id">{{ conversation.title }}</div>
                <div class="history-time">{{ new Date(conversation.createTime).toLocaleString() }}</div>
              </div>
            </div>

            <div class="history-actions">
              <el-button
                  type="danger"
                  size="small"
                  @click.stop="deleteConversation(conversation.id)"
                  :loading="deletingConversationId === conversation.id"
              >
                删除
              </el-button>
            </div>

            <div class="history-status">
              <el-tag
                  :type="conversation.id === currentConversationId ? 'success' : 'info'"
                  size="small"
              >
                {{ conversation.id === currentConversationId ? '当前对话' : '历史对话' }}
              </el-tag>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>

    <template #footer>
    <span class="dialog-footer">
      <el-button @click="showHistoryDialog = false">关闭</el-button>
      <el-button type="primary" @click="createNewConversation" :loading="creatingConversation">
        新建对话
      </el-button>
    </span>
    </template>
  </el-dialog>

  <!-- 发送题目对话框 -->
  <el-dialog
    v-model="showSendDialog"
    title="发送题目给学生"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form :model="sendForm" label-width="100px">
      <el-form-item label="选择课程">
        <el-select
          v-model="sendForm.courseCode"
          placeholder="请选择课程"
          style="width: 100%"
        >
          <el-option
            v-for="course in teacherCourses"
            :key="course.courseCode"
            :label="course.courseName"
            :value="course.courseCode"
          />
        </el-select>
      </el-form-item>
      
      <el-form-item label="题目标题">
        <el-input
          v-model="sendForm.title"
          placeholder="例如：第一章练习题"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button
          type="primary"
          @click="sendAssignmentToCourse"
          :loading="sendingAssignment"
        >
          发送
        </el-button>
      </span>
    </template>
  </el-dialog>

</template>

<style scoped>
.ai-teacher-container {
max-width: 1400px;
margin: 20px auto;
font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
height: calc(100vh - 40px);
display: flex;
flex-direction: column;
position: relative; /* 为右上角按钮定位提供容器 */
}

.header {
text-align: center;
margin-bottom: 20px;
}

.header h1 {
color: #409EFF;
margin-bottom: 8px;
}

.header p {
color: #666;
margin-bottom: 10px;
}

.header-buttons {
display: flex;
gap: 10px;
justify-content: center;
margin-top: 10px;
}

.main-container {
display: flex;
flex: 1;
gap: 20px;
height: 0;
}

.form-container {
width: 350px;
overflow: auto;
padding: 20px;
border: 1px solid #eaeaea;
border-radius: 8px;
background: #fafafa;
display: flex;
flex-direction: column;
}

.form-title {
font-size: 18px;
font-weight: bold;
color: #333;
}

.chat-container {
flex: 1;
overflow-y: auto;
padding: 10px;
border: 1px solid #eaeaea;
border-radius: 8px;
background: #fafafa;
}

.message {
margin-bottom: 16px;
}

.message-header {
display: flex;
justify-content: space-between;
align-items: center;
margin-bottom: 4px;
font-size: 14px;
}

.message-actions {
display: flex;
align-items: center;
gap: 10px;
}

.timestamp {
color: #999;
font-size: 12px;
}

.message-content {
border: none;
}

.message-content pre {
white-space: pre-wrap;
word-wrap: break-word;
margin: 0;
font-family: inherit;
line-height: 1.6;
}

.loading {
text-align: center;
padding: 20px;
color: #666;
}

.task-status {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.task-panel {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.task-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.task-panel-text {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}

.task-context-text {
  margin-top: 6px;
  color: #909399;
}

.task-panel-actions {
  margin-top: 10px;
}

.question-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.context-settings {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.context-settings-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.context-hint {
  margin-bottom: 10px;
  font-size: 12px;
  color: #409eff;
}

.question-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fcfcfd;
}

.question-card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.question-part-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 6px;
}

.question-part-title.solution {
  color: #67c23a;
}

.question-card-tags {
  display: flex;
  gap: 6px;
}

.question-card-stem {
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.6;
}

.question-card-options {
  background: #f7f8fa;
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 8px;
  line-height: 1.6;
}

.question-split-line {
  border-top: 1px dashed #dcdfe6;
  margin: 10px 0 8px;
}

.question-card-answer {
  color: #67c23a;
  font-weight: 600;
  margin-bottom: 6px;
}

.question-card-analysis {
  color: #606266;
  line-height: 1.6;
}

.quality-issues {
  border-top: 1px dashed #dcdfe6;
  padding-top: 8px;
}

.quality-issues-title {
  font-size: 13px;
  font-weight: 600;
  color: #e6a23c;
  margin-bottom: 4px;
}

.quality-issue-item {
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 768px) {
.main-container {
flex-direction: column;
}

.form-container {
width: 100%;
}
}

.history-dialog-content {
min-height: 100px;
}

.history-list {
margin-top: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #ebeef5;
  justify-content: space-between;
}

.history-item:hover {
  background-color: #f5f7fa;
  border-color: #c6e2ff;
}

.history-item.active {
  background-color: #ecf5ff;
  border-color: #409eff;
}


.history-item-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0; /* 防止内容溢出 */
}

.history-icon {
  font-size: 20px;
  color: #409eff;
  margin-right: 12px;
  flex-shrink: 0;
}

.history-info {
  flex: 1;
  min-width: 0; /* 防止内容溢出 */
}


.history-id {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-time {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-actions {
  margin: 0 12px;
  flex-shrink: 0;
}

.history-status {
  flex-shrink: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

<style>
.markdown-body {
font-family: inherit;
line-height: 1.6;
word-wrap: break-word;
}

.markdown-body pre {
background-color: #f6f8fa;
border-radius: 6px;
padding: 16px;
overflow: auto;
}

.markdown-body code {
background-color: rgba(110,118,129,0.4);
padding: 0.2em 0.4em;
border-radius: 6px;
font-size: 85%;
}

.markdown-body pre code {
background: none;
padding: 0;
}

.markdown-body blockquote {
margin: 0;
padding: 0 1em;
color: #57606a;
border-left: 0.25em solid #d0d7de;
}

.markdown-body ul, .markdown-body ol {
padding-left: 2em;
}

.markdown-body h1, .markdown-body h2, .markdown-body h3 {
margin-top: 24px;
margin-bottom: 16px;
font-weight: 600;
line-height: 1.25;
}

.markdown-body h1 {
font-size: 2em;
}

.markdown-body h2 {
font-size: 1.5em;
}

.markdown-body h3 {
font-size: 1.25em;
}

.markdown-body p {
margin-top: 0;
margin-bottom: 16px;
}

.markdown-body a {
color: #0969da;
text-decoration: none;
}

.markdown-body a:hover {
text-decoration: underline;
}

.markdown-body table {
border-spacing: 0;
border-collapse: collapse;
display: block;
width: max-content;
max-width: 100%;
overflow: auto;
}

.markdown-body td, .markdown-body th {
padding: 6px 13px;
border: 1px solid #d0d7de;
}

.markdown-body tr:nth-child(2n) {
background-color: #f6f8fa;
}

/* 填空题下划线样式 */
.markdown-body .fill-blank {
display: inline;
font-weight: normal;
font-style: normal;
text-decoration: underline;
text-decoration-style: solid;
text-decoration-thickness: 1px;
}

.form-title-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0px;
}

.form-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.form-title-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

</style>
