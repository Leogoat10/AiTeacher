<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import { Document, HeadingLevel, Packer, Paragraph, TextRun } from 'docx'
import { useRouter } from 'vue-router'
import { ChatDotRound } from '@element-plus/icons-vue'
import 'katex/dist/katex.min.css'

type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  timestamp: Date
  id: number
  contextUsed?: boolean
  contextRounds?: number
}

interface PresetPrompt {
  id: number
  title: string
  promptContent: string
  systemDefault: boolean
  teacherId?: number | null
  createdAt?: string
}

const CONTEXT_ROUNDS = 5
const TASK_POLL_INTERVAL_MS = 1000
const TASK_POLL_TIMEOUT_MS = 5 * 60 * 1000
const TASK_POLL_MAX_ATTEMPTS = Math.ceil(TASK_POLL_TIMEOUT_MS / TASK_POLL_INTERVAL_MS)
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const UNDERSCORE_PLACEHOLDER_PREFIX = 'UNDERSCOREBLANK'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()
const underscoreStore: Map<string, string> = new Map()

const router = useRouter()
const apiClient = axios.create({ baseURL: '/api', withCredentials: true })

marked.setOptions({ breaks: true, gfm: true })

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
  underscoreStore.clear()
  let counter = 0
  let underscoreCounter = 0
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
  replaced = autoWrapPlainMathExpressions(replaced, registerInlineFormula)
  replaced = replaced.replace(/_{2,}/g, (match) => {
    const placeholder = `${UNDERSCORE_PLACEHOLDER_PREFIX}${underscoreCounter}ENDUNDERSCORE`
    underscoreStore.set(placeholder, match)
    underscoreCounter += 1
    return placeholder
  })
  return replaced
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
  underscoreStore.forEach((underscores, placeholder) => {
    renderedHtml = renderedHtml.replace(new RegExp(placeholder, 'g'), `<span class="fill-blank">${underscores}</span>`)
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

const subject = ref('')
const grade = ref<string | string[]>('')
const teachingTopic = ref('')
const textbookVersion = ref('')
const durationMinutes = ref(45)
const interactionCount = ref(3)
const customRequirement = ref('')
const presetPrompts = ref<PresetPrompt[]>([])
const loadingPresetPrompts = ref(false)
const previewPresetPrompt = ref<PresetPrompt | null>(null)
const showPresetPreviewDialog = ref(false)
const showAddPresetDialog = ref(false)
const newPresetTitle = ref('')
const newPresetContent = ref('')
const creatingPresetPrompt = ref(false)
const contextInstruction = ref('')
const useRecentContext = ref(false)
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

const loading = ref(false)
const taskStatus = ref<TaskStatus | null>(null)
const taskErrorMessage = ref('')

const chatHistory = ref<ChatMessage[]>([])
const currentConversationId = ref<number | null>(null)
const creatingConversation = ref(false)
const loadingHistory = ref(false)
const showHistoryDialog = ref(false)
const historyConversations = ref<Array<{ id: number; createTime: string; title: string }>>([])
const deletingConversationId = ref<number | null>(null)
const selectedAiMessageIds = ref<Set<number>>(new Set())
let messageIdCounter = 0

const contextModeAvailable = computed(() => !!currentConversationId.value && chatHistory.value.some((item) => item.role === 'ai'))
const canSubmit = computed(() => !loading.value && (!!currentConversationId.value || !useRecentContext.value))
const aiMessageCount = computed(() => chatHistory.value.filter(item => item.role === 'ai').length)
const userMessageCount = computed(() => chatHistory.value.filter(item => item.role === 'user').length)
const exportableAiMessages = computed(() => chatHistory.value.filter((message) => isExportableAiMessage(message)))
const hasAnyExportableAiMessage = computed(() => exportableAiMessages.value.length > 0)
const hasSelectedAiMessage = computed(() => selectedAiMessageIds.value.size > 0)
const isAllAiMessageSelected = computed(() => {
  const messages = exportableAiMessages.value
  return messages.length > 0 && messages.every((message) => selectedAiMessageIds.value.has(message.id))
})
const normalizedGrade = computed(() => Array.isArray(grade.value) ? grade.value.toString() : grade.value)
const taskStatusType = computed(() => {
  if (taskStatus.value === 'FAILED') return 'danger'
  if (taskStatus.value === 'SUCCESS') return 'success'
  if (taskStatus.value === 'RUNNING') return 'warning'
  return 'info'
})
const taskStatusText = computed(() => {
  if (!taskStatus.value) return '空闲'
  if (taskStatus.value === 'PENDING') return '排队中'
  if (taskStatus.value === 'RUNNING') return '生成中'
  if (taskStatus.value === 'SUCCESS') return '已完成'
  return '失败'
})

const createNewConversation = async () => {
  creatingConversation.value = true
  try {
    const res = await apiClient.post('/teacher/lesson-plan/v1/newConversation')
    if (res.data.success) {
      currentConversationId.value = res.data.conversationId
      chatHistory.value = []
      selectedAiMessageIds.value = new Set()
      messageIdCounter = 0
      ElMessage.success('新对话已创建')
      return
    }
    ElMessage.error(res.data.error || '创建对话失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '创建对话失败')
  } finally {
    creatingConversation.value = false
  }
}

const fetchHistoryConversations = async () => {
  loadingHistory.value = true
  try {
    const res = await apiClient.get('/teacher/lesson-plan/v1/conversations')
    if (res.data.success) {
      historyConversations.value = res.data.conversations || []
      return
    }
    ElMessage.error(res.data.error || '获取历史对话失败')
  } catch (err: any) {
    if (err.response?.status === 401) {
      ElMessage.error('未登录或会话已过期，请先登录')
      router.push('/')
      return
    }
    ElMessage.error(err.response?.data?.error || '获取历史对话失败')
  } finally {
    loadingHistory.value = false
  }
}

const openHistoryDialog = async () => {
  showHistoryDialog.value = true
  await fetchHistoryConversations()
}

const switchToConversation = async (conversationId: number) => {
  try {
    const res = await apiClient.get(`/teacher/lesson-plan/v1/conversation/${conversationId}`)
    if (!res.data.success) {
      ElMessage.error(res.data.error || '切换对话失败')
      return
    }
    currentConversationId.value = conversationId
    chatHistory.value = []
    selectedAiMessageIds.value = new Set()
    messageIdCounter = 0
    const messages = Array.isArray(res.data.messages) ? res.data.messages : []
    messages.forEach((item: any) => {
      const role: 'user' | 'ai' = item.role === 'user' ? 'user' : 'ai'
      chatHistory.value.push({
        role,
        content: item.content || '',
        timestamp: item.timestamp ? new Date(item.timestamp) : new Date(),
        id: messageIdCounter++
      })
    })
    showHistoryDialog.value = false
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '切换对话失败')
  }
}

const deleteConversation = async (conversationId: number) => {
  const target = historyConversations.value.find((item) => item.id === conversationId)
  if (!target) return
  try {
    await ElMessageBox.confirm(`确认删除历史会话「${target.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  deletingConversationId.value = conversationId
  try {
    const res = await apiClient.delete(`/teacher/lesson-plan/v1/conversation/${conversationId}`)
    if (!res.data.success) {
      ElMessage.error(res.data.error || '删除失败')
      return
    }
    historyConversations.value = historyConversations.value.filter((item) => item.id !== conversationId)
    if (currentConversationId.value === conversationId) {
      currentConversationId.value = null
      chatHistory.value = []
      selectedAiMessageIds.value = new Set()
      messageIdCounter = 0
      await createNewConversation()
    }
    ElMessage.success('历史会话已删除')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '删除失败')
  } finally {
    deletingConversationId.value = null
  }
}

const fetchPresetPrompts = async () => {
  loadingPresetPrompts.value = true
  try {
    const res = await apiClient.get('/teacher/lesson-plan/v1/preset-prompts')
    if (res.data.success) {
      presetPrompts.value = Array.isArray(res.data.items) ? res.data.items : []
      return
    }
    ElMessage.error(res.data.error || '获取预设提示词失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '获取预设提示词失败')
  } finally {
    loadingPresetPrompts.value = false
  }
}

const applyPresetPrompt = (preset: PresetPrompt) => {
  const next = customRequirement.value.trim()
    ? `${customRequirement.value.trim()}\n${preset.promptContent}`
    : preset.promptContent
  customRequirement.value = next
  ElMessage.success('已填入补充要求')
}

const previewPreset = (preset: PresetPrompt) => {
  previewPresetPrompt.value = preset
  showPresetPreviewDialog.value = true
}

const openAddPresetDialog = () => {
  newPresetTitle.value = ''
  newPresetContent.value = ''
  showAddPresetDialog.value = true
}

const createPresetPrompt = async () => {
  if (!newPresetTitle.value.trim()) {
    ElMessage.warning('请输入预设名称')
    return
  }
  if (!newPresetContent.value.trim()) {
    ElMessage.warning('请输入预设内容')
    return
  }
  creatingPresetPrompt.value = true
  try {
    const res = await apiClient.post('/teacher/lesson-plan/v1/preset-prompts', {
      title: newPresetTitle.value.trim(),
      promptContent: newPresetContent.value.trim()
    })
    if (res.data.success) {
      ElMessage.success('预设添加成功')
      showAddPresetDialog.value = false
      await fetchPresetPrompts()
      return
    }
    ElMessage.error(res.data.error || '预设添加失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '预设添加失败')
  } finally {
    creatingPresetPrompt.value = false
  }
}

const deletePresetPrompt = async (preset: PresetPrompt) => {
  try {
    await ElMessageBox.confirm(`确认删除预设「${preset.title}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const res = await apiClient.delete(`/teacher/lesson-plan/v1/preset-prompts/${preset.id}`)
    if (res.data.success) {
      ElMessage.success('预设已删除')
      await fetchPresetPrompts()
      return
    }
    ElMessage.error(res.data.error || '删除失败')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.error || '删除失败')
  }
}

const isExportableAiMessage = (message: ChatMessage): boolean => {
  if (message.role !== 'ai') return false
  const content = message.content?.trim() || ''
  if (!content) return false
  return !content.startsWith('生成失败：') && !content.startsWith('任务状态：')
}

const toggleAiMessageSelection = (messageId: number) => {
  const selected = new Set(selectedAiMessageIds.value)
  if (selected.has(messageId)) {
    selected.delete(messageId)
  } else {
    selected.add(messageId)
  }
  selectedAiMessageIds.value = selected
}

const toggleSelectAllAiMessages = () => {
  if (isAllAiMessageSelected.value) {
    selectedAiMessageIds.value = new Set()
    return
  }
  selectedAiMessageIds.value = new Set(exportableAiMessages.value.map((message) => message.id))
}

const normalizeFormulaToken = (token: string): string => {
  if (token.startsWith('\\[') && token.endsWith('\\]')) return token.slice(2, -2).trim()
  if (token.startsWith('\\(') && token.endsWith('\\)')) return token.slice(2, -2).trim()
  if (token.startsWith('$$') && token.endsWith('$$')) return token.slice(2, -2).trim()
  if (token.startsWith('$') && token.endsWith('$')) return token.slice(1, -1).trim()
  return token.trim()
}

const parseInlineRuns = (line: string): TextRun[] => {
  if (!line) return [new TextRun('')]
  const runs: TextRun[] = []
  const tokenRegex = /(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+?\$|\*\*[^*]+\*\*|`[^`]+`)/g
  let lastIndex = 0
  let match: RegExpExecArray | null = tokenRegex.exec(line)
  while (match) {
    if (match.index > lastIndex) {
      runs.push(new TextRun(line.slice(lastIndex, match.index)))
    }
    const token = match[0]
    if (token.startsWith('**') && token.endsWith('**')) {
      runs.push(new TextRun({ text: token.slice(2, -2), bold: true }))
    } else if (
      (token.startsWith('\\[') && token.endsWith('\\]')) ||
      (token.startsWith('\\(') && token.endsWith('\\)')) ||
      (token.startsWith('$$') && token.endsWith('$$')) ||
      (token.startsWith('$') && token.endsWith('$'))
    ) {
      runs.push(new TextRun({ text: normalizeFormulaToken(token), font: 'Cambria Math' }))
    } else if (token.startsWith('`') && token.endsWith('`')) {
      runs.push(new TextRun({ text: token.slice(1, -1), font: 'Consolas' }))
    } else {
      runs.push(new TextRun(token))
    }
    lastIndex = tokenRegex.lastIndex
    match = tokenRegex.exec(line)
  }
  if (lastIndex < line.length) {
    runs.push(new TextRun(line.slice(lastIndex)))
  }
  return runs.length > 0 ? runs : [new TextRun('')]
}

const markdownToParagraphs = (markdown: string): Paragraph[] => {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const paragraphs: Paragraph[] = []
  let inCodeBlock = false
  for (const line of lines) {
    const raw = line || ''
    const trimmed = raw.trim()
    if (trimmed.startsWith('```')) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) {
      paragraphs.push(new Paragraph({ children: [new TextRun({ text: raw, font: 'Consolas' })] }))
      continue
    }
    if (!trimmed) {
      paragraphs.push(new Paragraph({}))
      continue
    }
    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      const level = headingMatch[1].length
      const text = headingMatch[2]
      const heading =
        level === 1 ? HeadingLevel.HEADING_1 :
          level === 2 ? HeadingLevel.HEADING_2 :
            level === 3 ? HeadingLevel.HEADING_3 :
              level === 4 ? HeadingLevel.HEADING_4 :
                level === 5 ? HeadingLevel.HEADING_5 : HeadingLevel.HEADING_6
      paragraphs.push(new Paragraph({ heading, children: parseInlineRuns(text) }))
      continue
    }
    const bulletMatch = trimmed.match(/^[-*]\s+(.+)$/)
    if (bulletMatch) {
      paragraphs.push(new Paragraph({
        bullet: { level: 0 },
        children: parseInlineRuns(bulletMatch[1])
      }))
      continue
    }
    const numberMatch = trimmed.match(/^(\d+)\.\s+(.+)$/)
    if (numberMatch) {
      paragraphs.push(new Paragraph({ children: parseInlineRuns(`${numberMatch[1]}. ${numberMatch[2]}`) }))
      continue
    }
    const quoteMatch = trimmed.match(/^>\s?(.+)$/)
    if (quoteMatch) {
      paragraphs.push(new Paragraph({ children: [new TextRun({ text: `引用：${quoteMatch[1]}`, italics: true })] }))
      continue
    }
    paragraphs.push(new Paragraph({ children: parseInlineRuns(trimmed) }))
  }
  return paragraphs
}

const createAndDownloadWord = async (messages: ChatMessage[]) => {
  const children: Paragraph[] = [
    new Paragraph({
      heading: HeadingLevel.TITLE,
      children: [new TextRun('AI教案导出')]
    }),
    new Paragraph({})
  ]

  messages.forEach((message, index) => {
    children.push(new Paragraph({
      heading: HeadingLevel.HEADING_1,
      children: [new TextRun(`第${index + 1}份教案`)]
    }))
    children.push(new Paragraph({ children: [new TextRun(`生成时间：${message.timestamp.toLocaleString()}`)] }))
    children.push(...markdownToParagraphs(message.content || ''))
    children.push(new Paragraph({}))
  })

  const wordDocument = new Document({
    sections: [{
      properties: {},
      children
    }]
  })
  const blob = await Packer.toBlob(wordDocument)
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `AI教案_${new Date().toISOString().slice(0, 10)}.docx`
  link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  ElMessage.success('导出成功')
}

const exportSelectedAiPlans = async () => {
  const selectedMessages = exportableAiMessages.value.filter((message) => selectedAiMessageIds.value.has(message.id))
  if (selectedMessages.length === 0) {
    ElMessage.warning('请先选择要导出的AI教案')
    return
  }
  try {
    await createAndDownloadWord(selectedMessages)
  } catch {
    ElMessage.error('导出失败，请重试')
  }
}

const normalizeTaskContent = (result: any): string => {
  if (!result || typeof result !== 'object') return ''
  return result.markdownContent || ''
}

const pollTaskResult = async (taskId: number) => {
  const maxAttempts = TASK_POLL_MAX_ATTEMPTS
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await new Promise((resolve) => setTimeout(resolve, TASK_POLL_INTERVAL_MS))
    const statusRes = await apiClient.get(`/teacher/lesson-plan/v1/tasks/${taskId}`)
    if (!statusRes.data.success) {
      throw new Error(statusRes.data.error || '查询任务状态失败')
    }
    const status = statusRes.data.status as TaskStatus
    taskStatus.value = status
    if (status === 'FAILED') {
      throw new Error(statusRes.data.errorMessage || '教案生成失败')
    }
    if (status === 'SUCCESS') {
      if (statusRes.data.planId) {
        const detailRes = await apiClient.get(`/teacher/lesson-plan/v1/${statusRes.data.planId}`)
        if (detailRes.data.success) {
          return { content: detailRes.data.markdownContent || '', statusPayload: statusRes.data }
        }
      }
      return { content: normalizeTaskContent(statusRes.data.result), statusPayload: statusRes.data }
    }
  }
  const finalStatusRes = await apiClient.get(`/teacher/lesson-plan/v1/tasks/${taskId}`)
  if (!finalStatusRes.data.success) {
    throw new Error(finalStatusRes.data.error || '查询任务状态失败')
  }
  const finalStatus = finalStatusRes.data.status as TaskStatus
  taskStatus.value = finalStatus
  if (finalStatus === 'FAILED') {
    throw new Error(finalStatusRes.data.errorMessage || '教案生成失败')
  }
  if (finalStatus === 'SUCCESS') {
    if (finalStatusRes.data.planId) {
      const detailRes = await apiClient.get(`/teacher/lesson-plan/v1/${finalStatusRes.data.planId}`)
      if (detailRes.data.success) {
        return { content: detailRes.data.markdownContent || '', statusPayload: finalStatusRes.data }
      }
    }
    return { content: normalizeTaskContent(finalStatusRes.data.result), statusPayload: finalStatusRes.data }
  }
  throw new Error('任务超时，请稍后在历史对话中查看')
}

const send = async () => {
  if (useRecentContext.value && !contextModeAvailable.value) {
    ElMessage.warning('当前会话暂无历史对话，无法使用参考上下文模式')
    return
  }
  if (useRecentContext.value && !contextInstruction.value.trim()) {
    ElMessage.warning('请输入上下文增量指令')
    return
  }
  if (!useRecentContext.value) {
    if (!subject.value.trim()) {
      ElMessage.warning('请输入科目')
      return
    }
    if (!normalizedGrade.value.trim()) {
      ElMessage.warning('请输入年级')
      return
    }
    if (!teachingTopic.value.trim()) {
      ElMessage.warning('请输入课题')
      return
    }
    if (!textbookVersion.value.trim()) {
      ElMessage.warning('请输入教材版本')
      return
    }
  }

  if (!currentConversationId.value) {
    await createNewConversation()
    if (!currentConversationId.value) return
  }

  const userMessage = useRecentContext.value
    ? `上下文增量指令：${contextInstruction.value.trim()} [关联最近${CONTEXT_ROUNDS}轮上下文]`
    : `${subject.value} ${normalizedGrade.value} ${teachingTopic.value} [${textbookVersion.value}] (${durationMinutes.value}分钟, 互动${interactionCount.value}个)`
  chatHistory.value.push({ role: 'user', content: userMessage, timestamp: new Date(), id: messageIdCounter++ })

  loading.value = true
  taskStatus.value = 'PENDING'
  taskErrorMessage.value = ''
  try {
    const payload = {
      subject: useRecentContext.value ? null : subject.value.trim(),
      grade: useRecentContext.value ? null : normalizedGrade.value.trim(),
      teachingTopic: useRecentContext.value ? null : teachingTopic.value.trim(),
      textbookVersion: useRecentContext.value ? null : textbookVersion.value.trim(),
      durationMinutes: useRecentContext.value ? null : durationMinutes.value,
      interactionCount: useRecentContext.value ? null : interactionCount.value,
      customRequirement: useRecentContext.value ? contextInstruction.value.trim() : (customRequirement.value.trim() || null),
      conversationId: currentConversationId.value,
      useContext: useRecentContext.value,
      contextRounds: CONTEXT_ROUNDS
    }
    const taskRes = await apiClient.post('/teacher/lesson-plan/v1/tasks', payload)
    if (!taskRes.data.success) {
      throw new Error(taskRes.data.error || '任务创建失败')
    }
    if (taskRes.data.newConversationId !== undefined && taskRes.data.newConversationId !== null) {
      currentConversationId.value = taskRes.data.newConversationId
    } else if (taskRes.data.conversationId !== undefined && taskRes.data.conversationId !== null) {
      currentConversationId.value = taskRes.data.conversationId
    }

    const polled = await pollTaskResult(Number(taskRes.data.taskId))
    chatHistory.value.push({
      role: 'ai',
      content: polled.content || '教案生成成功，但无可展示内容',
      contextUsed: !!(polled.statusPayload.useContext ?? payload.useContext),
      contextRounds: Number(polled.statusPayload.contextRounds ?? payload.contextRounds ?? CONTEXT_ROUNDS),
      timestamp: new Date(),
      id: messageIdCounter++
    })
    ElMessage.success('教案生成完成')
  } catch (err: any) {
    taskStatus.value = 'FAILED'
    taskErrorMessage.value = err?.message || '未知错误'
    chatHistory.value.push({ role: 'ai', content: `生成失败：${taskErrorMessage.value}`, timestamp: new Date(), id: messageIdCounter++ })
    ElMessage.error(taskErrorMessage.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([createNewConversation(), fetchPresetPrompts()])
})
</script>

<template>
  <div class="ai-plan-container">
    <section class="left-panel">
      <header class="panel-header">
        <div>
          <h2>AI 教案工作台</h2>
          <p>输入教学需求，快速生成结构化教案</p>
        </div>
        <div class="panel-actions">
          <el-button size="small" @click="createNewConversation" :loading="creatingConversation">
            <el-icon><ChatDotRound /></el-icon>
            新建
          </el-button>
          <el-button size="small" @click="openHistoryDialog">历史</el-button>
        </div>
      </header>

      <div class="status-overview">
        <div class="status-card">
          <span class="status-label">当前状态</span>
          <el-tag :type="taskStatusType" effect="light">{{ taskStatusText }}</el-tag>
        </div>
        <div class="status-card">
          <span class="status-label">对话轮次</span>
          <strong>{{ userMessageCount }} / {{ aiMessageCount }}</strong>
        </div>
      </div>

      <el-form label-position="top" class="plan-form">
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
            当前会话暂无历史教案，暂不可启用参考上下文模式
          </div>
        </el-form-item>

        <template v-if="!useRecentContext">
          <el-form-item label="科目">
            <el-input v-model="subject" placeholder="例如：数学" :disabled="loading" />
          </el-form-item>
          <el-form-item label="年级">
            <el-cascader
              v-model="grade"
              :options="educationOptions"
              :props="{ expandTrigger: 'hover' }"
              placeholder="请选择年级"
              style="width: 100%"
              :disabled="loading"
            />
          </el-form-item>
          <el-form-item label="课题">
            <el-input v-model="teachingTopic" placeholder="例如：一元一次方程应用" :disabled="loading" />
          </el-form-item>
          <el-form-item label="教材版本">
            <el-input v-model="textbookVersion" placeholder="例如：人教版" :disabled="loading" />
          </el-form-item>

          <div class="number-grid">
            <el-form-item label="课时长度(分钟)">
              <el-input-number v-model="durationMinutes" :min="20" :max="180" :disabled="loading" />
            </el-form-item>
            <el-form-item label="互动环节数量">
              <el-input-number v-model="interactionCount" :min="3" :max="12" :disabled="loading" />
            </el-form-item>
          </div>

          <el-form-item label="补充要求（选填）">
            <el-input
              v-model="customRequirement"
              type="textarea"
              :rows="4"
              :disabled="loading"
              placeholder="例如：加强分层提问，控制课堂节奏"
            />
            <div class="preset-prompt-panel">
              <div class="preset-prompt-header">
                <span>预设 Prompt</span>
                <div class="preset-prompt-actions">
                  <el-button size="small" text @click="fetchPresetPrompts" :loading="loadingPresetPrompts">刷新</el-button>
                  <el-button size="small" text type="primary" @click="openAddPresetDialog">新增</el-button>
                </div>
              </div>
              <el-empty
                v-if="!loadingPresetPrompts && presetPrompts.length === 0"
                description="暂无预设Prompt"
                :image-size="56"
              />
              <div v-else class="preset-prompt-list">
                <div v-for="preset in presetPrompts" :key="preset.id" class="preset-prompt-item">
                  <div class="preset-prompt-main">
                    <div class="preset-prompt-title">
                      <span>{{ preset.title }}</span>
                      <el-tag size="small" :type="preset.systemDefault ? 'success' : 'info'" effect="plain">
                        {{ preset.systemDefault ? '系统默认' : '我的新增' }}
                      </el-tag>
                    </div>
                    <div class="preset-prompt-preview">{{ preset.promptContent }}</div>
                  </div>
                  <div class="preset-prompt-item-actions">
                    <el-button size="small" text @click="previewPreset(preset)">预览</el-button>
                    <el-button size="small" text type="primary" @click="applyPresetPrompt(preset)">使用</el-button>
                    <el-button
                      v-if="!preset.systemDefault"
                      size="small"
                      text
                      type="danger"
                      @click="deletePresetPrompt(preset)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="上下文增量指令">
            <el-input
              v-model="contextInstruction"
              placeholder="例如：保持课题不变，把互动环节改成分组探究并提高难度"
              type="textarea"
              :rows="6"
              :disabled="loading"
            />
          </el-form-item>
        </template>

        <el-button type="primary" class="submit-btn" @click="send" :loading="loading" :disabled="!canSubmit">
          生成教案
        </el-button>
        <div class="task-panel-error" v-if="taskStatus === 'FAILED'">失败原因：{{ taskErrorMessage || '未知错误' }}</div>
      </el-form>
    </section>

    <section class="right-panel">
      <header class="chat-header">
        <h3>教案对话</h3>
        <div class="chat-actions">
          <span class="chat-sub">AI 输出支持 Markdown / LaTeX 渲染</span>
          <el-button
            size="small"
            @click="toggleSelectAllAiMessages"
            :disabled="!hasAnyExportableAiMessage"
          >
            {{ isAllAiMessageSelected ? '取消全选教案' : '全选教案' }}
          </el-button>
          <el-button
            size="small"
            type="success"
            @click="exportSelectedAiPlans"
            :disabled="!hasSelectedAiMessage"
          >
            导出Word
          </el-button>
        </div>
      </header>

      <div class="chat-body">
        <div
          v-for="message in chatHistory"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="avatar">{{ message.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="message-wrap">
            <div class="message-header">
              <strong>{{ message.role === 'user' ? '您' : 'AI助手' }}</strong>
              <div class="message-meta">
                <el-button
                  v-if="isExportableAiMessage(message)"
                  text
                  type="primary"
                  size="small"
                  @click="toggleAiMessageSelection(message.id)"
                >
                  {{ selectedAiMessageIds.has(message.id) ? '已选择导出' : '选择导出' }}
                </el-button>
                <span class="timestamp">{{ message.timestamp.toLocaleTimeString() }}</span>
              </div>
            </div>
            <el-card :shadow="message.role === 'user' ? 'never' : 'hover'" :class="['message-content', message.role]">
              <template v-if="message.role === 'ai'">
                <div class="context-hint" v-if="message.contextUsed !== undefined">
                  {{ message.contextUsed ? `本次已关联最近${message.contextRounds || CONTEXT_ROUNDS}轮上下文` : '本次未关联历史上下文（纯参数生成）' }}
                </div>
                <div class="markdown-body" v-html="renderMarkdown(message.content)"></div>
              </template>
              <template v-else>
                <div class="user-content">{{ message.content }}</div>
              </template>
            </el-card>
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="showHistoryDialog" title="历史对话" width="720px">
      <div class="history-dialog-content">
        <el-empty v-if="historyConversations.length === 0 && !loadingHistory" description="暂无历史对话" />
        <div v-else class="history-list">
          <div
            v-for="conversation in historyConversations"
            :key="conversation.id"
            class="history-item"
            :class="{ active: conversation.id === currentConversationId }"
          >
            <div class="history-item-content" @click="switchToConversation(conversation.id)">
              <el-icon class="history-icon"><ChatDotRound /></el-icon>
              <div class="history-info">
                <div class="history-id">{{ conversation.title }}</div>
                <div class="history-time">{{ new Date(conversation.createTime).toLocaleString() }}</div>
              </div>
            </div>
            <div class="history-actions">
              <el-button
                size="small"
                text
                type="danger"
                :loading="deletingConversationId === conversation.id"
                @click.stop="deleteConversation(conversation.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showPresetPreviewDialog" title="预设Prompt预览" width="640px">
      <div class="preset-preview-content">{{ previewPresetPrompt?.promptContent }}</div>
    </el-dialog>

    <el-dialog v-model="showAddPresetDialog" title="新增预设Prompt" width="640px">
      <el-form label-position="top">
        <el-form-item label="预设名称">
          <el-input v-model="newPresetTitle" maxlength="100" placeholder="例如：探究互动加强版" />
        </el-form-item>
        <el-form-item label="预设内容">
          <el-input
            v-model="newPresetContent"
            type="textarea"
            :rows="6"
            maxlength="3000"
            show-word-limit
            placeholder="请输入希望填入“补充要求”的Prompt内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddPresetDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingPresetPrompt" @click="createPresetPrompt">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-plan-container {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  width: 100%;
  min-height: calc(100vh - 120px);
  background: linear-gradient(180deg, #f8fbff 0%, #f3f6fb 100%);
  border-radius: 14px;
}

.left-panel,
.right-panel {
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.left-panel {
  padding: 18px;
  border: 1px solid #e8eef8;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.panel-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.panel-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.status-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.status-card {
  border-radius: 10px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-label {
  color: #64748b;
  font-size: 12px;
}

.plan-form {
  margin-top: 8px;
}

.number-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 4px;
  border-radius: 10px;
  font-weight: 600;
}

.context-settings {
  display: flex;
  align-items: center;
  gap: 8px;
}

.context-settings-tip {
  margin-top: 8px;
  padding: 8px 10px;
  font-size: 12px;
  color: #7c8ba1;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #d3dce6;
}

.task-panel-error {
  color: #dc2626;
  margin-top: 10px;
  font-size: 13px;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  padding: 8px 10px;
  border-radius: 8px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  min-height: 720px;
  border: 1px solid #e8eef8;
}

.chat-header {
  padding: 16px 18px;
  border-bottom: 1px solid #edf2f7;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header h3 {
  margin: 0;
  font-size: 17px;
  color: #111827;
}

.chat-sub {
  color: #94a3b8;
  font-size: 12px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.message-row {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 10px;
  margin-bottom: 14px;
}

.message-row.user {
  grid-template-columns: 1fr 34px;
}

.message-row.user .avatar {
  order: 2;
}

.message-row.user .message-wrap {
  order: 1;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  border: 1px solid #dbeafe;
  font-size: 16px;
}

.message-content {
  border-radius: 12px;
}

.timestamp {
  color: #9aa7b9;
  font-size: 12px;
}

.message-content.user {
  background: #eef6ff;
  border: 1px solid #dbeafe;
}

.message-content.ai {
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.user-content {
  white-space: pre-wrap;
  line-height: 1.75;
  color: #1e293b;
}

.context-hint {
  margin-bottom: 12px;
  color: #2563eb;
  font-size: 12px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  padding: 6px 10px;
}

:deep(.markdown-body) {
  line-height: 1.85;
  color: #334155;
  font-size: 14px;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: #0f172a;
}

:deep(.markdown-body p) {
  margin: 7px 0;
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  padding-left: 20px;
  margin: 8px 0;
}

:deep(.markdown-body blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #93c5fd;
  background: #f8fbff;
  color: #475569;
}

:deep(.fill-blank) {
  letter-spacing: 1.2px;
  color: #111827;
}

.history-dialog-content {
  max-height: 520px;
  overflow-y: auto;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  transition: all 0.2s ease;
}

.history-item:hover {
  border-color: #93c5fd;
  background: #f8fbff;
}

.history-item.active {
  border-color: #409eff;
  background: #f5faff;
}

.history-item-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.history-actions {
  display: flex;
  align-items: center;
}

.history-icon {
  color: #409eff;
  margin-top: 2px;
}

.history-id {
  font-weight: 600;
}

.history-time {
  color: #909399;
  font-size: 12px;
}

.preset-prompt-panel {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #f8fafc;
}

.preset-prompt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.preset-prompt-actions {
  display: flex;
  gap: 6px;
}

.preset-prompt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.preset-prompt-item {
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #fff;
  padding: 8px 10px;
}

.preset-prompt-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preset-prompt-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #0f172a;
  font-weight: 600;
}

.preset-prompt-preview {
  font-size: 12px;
  color: #64748b;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.preset-prompt-item-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.preset-preview-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #334155;
  max-height: 420px;
  overflow-y: auto;
}

@media (max-width: 1180px) {
  .ai-plan-container {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .right-panel {
    min-height: 620px;
  }
}
</style>
