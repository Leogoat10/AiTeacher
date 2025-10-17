# LaTeX 数学公式渲染实现文档

## 文档信息

- **项目**: AiTeacher 智能教师系统
- **模块**: 前端题目生成页面 (TeacherQuestion.vue)
- **功能**: 将 DeepSeek API 返回的 LaTeX 数学公式正确渲染为可视化的数学表达式
- **创建日期**: 2025-10-17
- **版本**: v2.0 (占位符保护法)

---

## 目录

1. [问题背景](#问题背景)
2. [问题分析](#问题分析)
3. [解决方案演进](#解决方案演进)
4. [最终实现方案](#最终实现方案)
5. [技术细节](#技术细节)
6. [代码实现](#代码实现)
7. [测试验证](#测试验证)
8. [使用指南](#使用指南)
9. [常见问题](#常见问题)

---

## 问题背景

### 初始状况

在智能教师系统中，DeepSeek API 返回的题目内容包含大量 LaTeX 格式的数学公式，例如：

```latex
已知函数 \( f(x) = \sin(2x + \frac{\pi}{3}) \)，则 \( f'(x) = \) ______。

导数为：
\[
f'(x) = \cos(2x + \frac{\pi}{3}) \cdot (2x + \frac{\pi}{3})' = \cos(2x + \frac{\pi}{3}) \cdot 2
\]
```

### 问题现象

前端页面显示时，数学公式无法正确渲染，表现为：

1. **公式原样显示**: `\( f(x) = \sin(2x + \frac{\pi}{3}) \)` 直接显示为文本
2. **HTML 实体编码问题**: 公式中的单引号被转义为 `&#39;`，显示为：
   ```
   f&#39;(x) = \cos(2x + \frac{\pi}{3}) \cdot (2x + \frac{\pi}{3})&#39;
   ```
3. **部分公式显示为**: `$$ ... $$` 或 `$ ... $` 格式，但未被渲染

---

## 问题分析

### 根本原因

1. **LaTeX 语法不兼容**
   - DeepSeek 返回的是标准 LaTeX 语法：`\(...\)` (行内) 和 `\[...\]` (块级)
   - Marked.js 的 KaTeX 扩展期望的是：`$...$` (行内) 和 `$$...$$` (块级)

2. **Markdown 解析顺序问题**
   - 直接转换 `\(...\)` → `$...$` 后，Markdown 解析器会处理特殊字符
   - 单引号 `'` 被转义为 HTML 实体 `&#39;`
   - 转义后的公式无法被 KaTeX 识别和渲染

3. **依赖版本兼容性**
   - `marked-katex-extension` v5.x 与 `marked` v16.x 存在兼容性问题
   - 配置不当导致公式无法被扩展识别

### 技术栈分析

```
DeepSeek API (LaTeX: \(...\), \[...\])
    ↓
Vue 3 前端接收
    ↓
Marked.js 解析 Markdown (导致特殊字符转义)
    ↓
KaTeX 渲染公式 (无法识别已转义的内容)
    ↓
DOMPurify 清理
    ↓
显示 (❌ 公式未渲染)
```

---

## 解决方案演进

### 方案 1: 使用 marked-katex-extension (失败)

#### 实现思路
使用 `marked-katex-extension` 插件，配置 marked 直接支持 LaTeX 渲染。

#### 代码示例
```typescript
import { marked } from 'marked'
import markedKatex from 'marked-katex-extension'

marked.use(markedKatex({
  throwOnError: false,
  output: 'html'
}))

// 转换语法
const converted = markdownContent.replace(/\\\[([\s\S]*?)\\\]/g, '$$$$1$$')
                                  .replace(/\\\(([\s\S]*?)\\\)/g, '$$$1$$')
const html = await marked.parse(converted)
```

#### 失败原因
- `marked-katex-extension` v5.x 与 `marked` v16.x 版本不兼容
- 公式仍然显示为 `$$...$$` 文本，未被渲染
- 配置参数无法正确传递到 KaTeX

---

### 方案 2: 直接正则替换 + KaTeX 渲染 (部分失败)

#### 实现思路
先用 Marked 解析 Markdown，再在 HTML 中查找 `$...$` 和 `$$...$$` 并用 KaTeX 渲染。

#### 代码示例
```typescript
const convertLatexSyntax = (text: string): string => {
  let result = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => `$$${formula}$$`)
  result = result.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => `$${formula}$`)
  return result
}

const renderLatexInHTML = (html: string): string => {
  html = html.replace(/\$\$([\s\S]+?)\$\$/g, (match, formula) => {
    return katex.renderToString(formula.trim(), { displayMode: true })
  })
  html = html.replace(/\$([^\$]+?)\$/g, (match, formula) => {
    return katex.renderToString(formula.trim(), { displayMode: false })
  })
  return html
}
```

#### 失败原因
- Markdown 解析器在处理 `$...$` 时，会转义内部的特殊字符
- 单引号 `'` → `&#39;`，导致 KaTeX 无法识别
- 正则表达式在 HTML 中匹配公式时可能匹配到被标签分割的内容

---

### 方案 3: 占位符保护法 (✅ 成功)

#### 核心思想
**在 Markdown 解析之前**提取并保护 LaTeX 公式，避免特殊字符被转义。

#### 实现流程
```
原始内容 (LaTeX: \(...\), \[...\])
    ↓ 步骤1: 提取公式并替换为占位符
占位符文本 (LATEXFORMULAINLINE0ENDLATEX)
    ↓ 步骤2: 安全解析 Markdown
HTML (占位符保持不变)
    ↓ 步骤3: 渲染公式并替换占位符
HTML (包含 KaTeX 渲染结果)
    ↓ 步骤4: 清理 HTML
最终显示 (✅ 公式正确渲染)
```

#### 优势
✅ 公式内容在 Markdown 解析时不会被修改  
✅ 特殊字符得到完整保护  
✅ 不依赖第三方扩展的版本兼容性  
✅ 对公式渲染有完全控制权  
✅ 错误处理更灵活  

---

## 最终实现方案

### 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    DeepSeek API 响应                      │
│  包含 \(...\) 行内公式和 \[...\] 块级公式                │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│            步骤 1: protectLatexFormulas()                │
│  • 提取所有 LaTeX 公式                                    │
│  • 存储到 Map 中 (公式内容 + 显示模式)                    │
│  • 替换为占位符 (LATEXFORMULAINLINE0ENDLATEX)            │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│            步骤 2: marked.parse()                        │
│  • 解析 Markdown 语法 (加粗、斜体、列表等)                │
│  • 占位符保持不变，不会被转义                             │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│            步骤 3: renderProtectedLatex()                │
│  • 遍历 Map 中的公式                                      │
│  • 使用 KaTeX 渲染每个公式                                │
│  • 在 HTML 中替换对应的占位符                             │
└──────────────────────┬──────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────┐
│            步骤 4: DOMPurify.sanitize()                  │
│  • 清理潜在的 XSS 攻击                                    │
│  • 保留 KaTeX 生成的安全 HTML                             │
└──────────────────────┬──────────────────────────────────┘
                       ↓
                  最终渲染显示
```

### 关键设计决策

#### 1. 占位符格式设计

**选择**: `LATEXFORMULAINLINE0ENDLATEX` 和 `LATEXFORMULADISPLAY0ENDLATEX`

**原因**:
- ❌ `___LATEX_PLACEHOLDER_0_DISPLAY___` - 包含下划线，会被 Markdown 解析为斜体/粗体
- ❌ `{{LATEX_0}}` - 可能与模板语法冲突
- ❌ `<LATEX_0>` - 会被 HTML 解析器处理
- ✅ `LATEXFORMULAINLINE0ENDLATEX` - 纯字母数字，不会被任何解析器特殊处理

#### 2. 数据存储结构

```typescript
const latexFormulaStore: Map<string, { 
  formula: string;        // 公式内容
  displayMode: boolean;   // 显示模式 (行内 or 块级)
}> = new Map()
```

**优势**:
- 使用 Map 确保 O(1) 查找效率
- 存储显示模式，避免重复判断
- 每次处理前 clear()，避免不同请求间的污染

#### 3. 正则表达式设计

```typescript
// 块级公式: \[...\]
/\\\[([\s\S]*?)\\\]/g

// 行内公式: \(...\)
/\\\(([\s\S]*?)\\\)/g
```

**细节**:
- `[\s\S]*?` - 匹配包括换行符在内的任意字符（非贪婪）
- `\\\[` 和 `\\\]` - 转义匹配字面的 `[` 和 `]`
- `g` 标志 - 全局匹配所有出现的公式

---

## 技术细节

### 依赖包版本

```json
{
  "dependencies": {
    "marked": "^16.3.0",
    "katex": "^0.16.25",
    "dompurify": "^3.x.x"
  }
}
```

### KaTeX 配置参数

```typescript
{
  displayMode: boolean,    // true: 块级居中, false: 行内
  throwOnError: false,     // 渲染失败时不抛出异常，返回原文本
  output: 'html'          // 输出 HTML 格式（还支持 'mathml', 'htmlAndMathml'）
}
```

### 占位符计数器设计

```typescript
let counter = 0  // 每次 protectLatexFormulas() 调用时重置

// 为什么需要计数器？
// 1. 确保每个占位符唯一
// 2. 保持占位符与公式的一一对应关系
// 3. 便于调试时追踪特定公式
```

---

## 代码实现

### 完整实现代码

```vue
<!-- TeacherQuestion.vue 关键代码 -->

<script setup lang="ts">
import { ref } from 'vue'
import { marked } from 'marked'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'

// -------------------- 配置 --------------------

// 占位符前缀，用于保护 LaTeX 公式
const LATEX_PLACEHOLDER_PREFIX = 'LATEXFORMULA'
const latexFormulaStore: Map<string, { formula: string; displayMode: boolean }> = new Map()

// -------------------- 核心函数 --------------------

/**
 * 提取并保护 LaTeX 公式
 * @param text - 包含 LaTeX 公式的原始文本
 * @returns 公式被占位符替换后的文本
 */
const protectLatexFormulas = (text: string): string => {
  latexFormulaStore.clear()
  let counter = 0
  
  // 先处理块级公式 \[...\]
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { 
      formula: formula.trim(), 
      displayMode: true 
    })
    counter++
    return placeholder
  })
  
  // 再处理行内公式 \(...\)
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { 
      formula: formula.trim(), 
      displayMode: false 
    })
    counter++
    return placeholder
  })
  
  return text
}

/**
 * 渲染被保护的 LaTeX 公式
 * @param html - 包含占位符的 HTML
 * @returns 占位符被 KaTeX 渲染结果替换后的 HTML
 */
const renderProtectedLatex = (html: string): string => {
  latexFormulaStore.forEach((data, placeholder) => {
    try {
      const rendered = katex.renderToString(data.formula, {
        displayMode: data.displayMode,
        throwOnError: false,
        output: 'html'
      })
      // 替换所有出现的占位符
      html = html.replace(new RegExp(placeholder, 'g'), rendered)
    } catch (e) {
      console.error('KaTeX render error:', e, 'Formula:', data.formula)
    }
  })
  
  return html
}

// -------------------- 使用示例 --------------------

// 场景 1: 发送新消息时
const send = async () => {
  const res = await apiClient.post('/teacher/question', formData)
  
  if (res.data.success) {
    const markdownContent = res.data.reply || ''
    
    // 应用四步渲染流程
    const protectedText = protectLatexFormulas(markdownContent)
    const html = await marked.parse(protectedText)
    const withLatex = renderProtectedLatex(html)
    const htmlContent = DOMPurify.sanitize(withLatex)
    
    chatHistory.value.push({
      role: 'ai',
      content: htmlContent,
      rawContent: markdownContent,
      timestamp: new Date(),
      id: messageIdCounter++
    })
  }
}

// 场景 2: 加载历史对话时
const switchToConversation = async (conversationId: number) => {
  const res = await apiClient.get(`/teacher/conversation/${conversationId}`)
  
  if (res.data.success) {
    const messages = res.data.messages || []
    
    for (const msg of messages) {
      let content = msg.content
      
      if (msg.role === 'ai') {
        const protectedText = protectLatexFormulas(msg.content)
        const html = await marked.parse(protectedText)
        const withLatex = renderProtectedLatex(html)
        content = DOMPurify.sanitize(withLatex)
      }
      
      chatHistory.value.push({
        role: msg.role,
        content,
        rawContent: msg.role === 'ai' ? msg.content : undefined,
        timestamp: new Date(msg.timestamp),
        id: messageIdCounter++
      })
    }
  }
}
</script>
```

### protectLatexFormulas() 详解

```typescript
const protectLatexFormulas = (text: string): string => {
  // 清空上次的存储，避免污染
  latexFormulaStore.clear()
  let counter = 0
  
  // 处理顺序很重要！先处理块级，再处理行内
  // 原因：避免 \[...\] 内部的 \(...\) 被错误识别
  
  // 块级公式
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (match, formula) => {
    // match: 完整匹配 "\[ ... \]"
    // formula: 捕获组内容 "..."
    
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}DISPLAY${counter}ENDLATEX`
    
    // 存储公式内容和显示模式
    latexFormulaStore.set(placeholder, { 
      formula: formula.trim(),  // trim() 去除首尾空白
      displayMode: true         // 块级公式
    })
    
    counter++
    return placeholder  // 返回占位符替换原文本
  })
  
  // 行内公式（同样的逻辑）
  text = text.replace(/\\\(([\s\S]*?)\\\)/g, (match, formula) => {
    const placeholder = `${LATEX_PLACEHOLDER_PREFIX}INLINE${counter}ENDLATEX`
    latexFormulaStore.set(placeholder, { 
      formula: formula.trim(), 
      displayMode: false  // 行内公式
    })
    counter++
    return placeholder
  })
  
  return text
}
```

**处理示例**:

输入:
```latex
已知函数 \( f(x) = \sin(x) \)，则导数为：
\[
f'(x) = \cos(x)
\]
```

输出:
```
已知函数 LATEXFORMULAINLINE0ENDLATEX，则导数为：
LATEXFORMULADISPLAY1ENDLATEX
```

存储:
```javascript
Map {
  'LATEXFORMULAINLINE0ENDLATEX' => { formula: 'f(x) = \\sin(x)', displayMode: false },
  'LATEXFORMULADISPLAY1ENDLATEX' => { formula: "f'(x) = \\cos(x)", displayMode: true }
}
```

---

## 测试验证

### 测试用例 1: 行内公式

**输入**:
```latex
已知函数 \( f(x) = \sin(2x + \frac{\pi}{3}) \)，求导数。
```

**期望**: 公式正确渲染，无转义字符  
**结果**: ✅ 通过

---

### 测试用例 2: 块级公式（问题公式）

**输入**:
```latex
\[
f'(x) = \cos(2x + \frac{\pi}{3}) \cdot (2x + \frac{\pi}{3})' = \cos(2x + \frac{\pi}{3}) \cdot 2
\]
```

**之前的错误**: `f&#39;(x) = ...`  
**现在的结果**: ✅ 单引号正确渲染，公式完整显示

---

### 测试用例 3: 混合公式

**输入**:
```latex
函数 \( f(x) = \sin(x) \) 的导数为：
\[
f'(x) = \cos(x)
\]
因此 \( f'(0) = 1 \)。
```

**结果**: ✅ 行内和块级公式都正确渲染

---

### 验证脚本

```javascript
// test-katex-rendering.js
import katex from 'katex';
import { marked } from 'marked';

// ... 复制核心函数 ...

const problematicFormula = `\\[
f'(x) = \\cos(2x + \\frac{\\pi}{3}) \\cdot (2x + \\frac{\\pi}{3})'
\\]`;

const protectedText = protectLatexFormulas(problematicFormula);
const html = await marked.parse(protectedText);
const result = renderProtectedLatex(html);

// 验证
console.log('包含 KaTeX HTML:', result.includes('class="katex"'));
console.log('无 HTML 实体问题:', !result.includes('&#39;'));
console.log('无未渲染的 LaTeX:', !result.includes('\\cos'));
```

---

## 使用指南

### 1. 安装依赖

```bash
cd frontend
npm install katex marked dompurify
npm install --save-dev @types/katex @types/marked
```

### 2. 在 Vue 组件中使用

```vue
<template>
  <div class="chat-container">
    <div 
      v-for="msg in chatHistory" 
      :key="msg.id"
      class="message"
      v-html="msg.content"
    ></div>
  </div>
</template>

<script setup lang="ts">
import 'katex/dist/katex.min.css'  // 重要：引入样式
// ... 实现核心函数 ...
</script>
```

### 3. 处理 API 响应

```typescript
const processAIResponse = async (markdownContent: string) => {
  // 四步流程
  const protectedText = protectLatexFormulas(markdownContent)
  const html = await marked.parse(protectedText)
  const withLatex = renderProtectedLatex(html)
  const cleanHtml = DOMPurify.sanitize(withLatex)
  
  return cleanHtml
}
```

---

## 常见问题

### Q1: 为什么不用 marked-katex-extension？

**A**: 版本兼容性问题，且占位符法提供更好的控制和灵活性。

### Q2: 占位符会不会冲突？

**A**: `LATEXFORMULAINLINE0ENDLATEX` 格式极不可能出现在正常文本中。

### Q3: 为什么先处理块级后处理行内？

**A**: 避免块级公式内部的行内公式被错误识别和替换。

### Q4: KaTeX 渲染失败怎么办？

**A**: 
```typescript
try {
  const rendered = katex.renderToString(formula, {
    throwOnError: false  // 失败时返回原文本
  })
} catch (e) {
  console.error('渲染失败:', e)
  // 显示错误提示
}
```

### Q5: 如何自定义样式？

**A**:
```css
.katex { font-size: 1.2em; }
.katex-display { margin: 1.5em 0; }
```

---

## 总结

### 核心要点

1. **占位符保护法**: 在 Markdown 解析前提取公式
2. **四步流程**: 提取 → 解析 → 渲染 → 清理
3. **完整保护**: 特殊字符不会被转义
4. **灵活可控**: 完全控制渲染行为

### 优势

✅ 兼容性强 - 不依赖扩展版本  
✅ 可靠性高 - 公式完整保护  
✅ 可维护性好 - 代码清晰易懂  
✅ 扩展性强 - 易于定制  
✅ 性能优秀 - 高效处理  

### 适用场景

- 在线教育平台
- 数学/物理类 AI 助手
- 科研论文编辑器
- 技术文档系统
- 任何需要 LaTeX 渲染的 Web 应用

---

## 附录

### A. KaTeX 支持的命令

- 分数: `\frac{a}{b}`
- 上下标: `x^2`, `x_i`
- 根号: `\sqrt{x}`
- 求和: `\sum_{i=1}^{n}`
- 积分: `\int_a^b`
- 希腊字母: `\alpha`, `\beta`, `\pi`
- 三角函数: `\sin`, `\cos`, `\tan`
- 括号: `\left(`, `\right)`, `\left\{`, `\right\}`

### B. 相关资源

- **KaTeX 官方**: https://katex.org/
- **Marked 文档**: https://marked.js.org/
- **LaTeX 符号**: https://oeis.org/wiki/List_of_LaTeX_mathematical_symbols

---

**版本**: v2.0  
**日期**: 2025-10-17  
**维护**: AiTeacher Team
