<template>
  <div class="welcome-page">
    <div class="main-container">
      <div class="logo">
        <div class="logo-icon-wrapper">
          <i class="fas fa-gem logo-icon"></i>
        </div>
      </div>

      <h1 class="title">AI智能教师</h1>

      <p class="description">
        只有学习精彩，生命才精彩；只有学习成功，事业才成功<br />
        AI智能教师<br />
        请选择你的身份
      </p>

      <div class="role-buttons">
        <div class="card role-card student-card">
          <div class="card-body text-center">
            <div class="role-icon-wrapper">
              <i class="fas fa-user-graduate role-icon text-primary"></i>
            </div>
            <h5 class="card-title"><i class="fas fa-user-graduate title-icon"></i> 学生</h5>
            <p class="card-text">进入学生登录页面</p>
            <el-button type="primary" class="btn-role btn-student" @click="navigateTo('/studentLogin')">
              进入
            </el-button>
          </div>
        </div>

        <div class="card role-card teacher-card">
          <div class="card-body text-center">
            <div class="role-icon-wrapper">
              <i class="fas fa-chalkboard-teacher role-icon text-success"></i>
            </div>
            <h5 class="card-title"><i class="fas fa-chalkboard-teacher title-icon"></i> 教师</h5>
            <p class="card-text">进入教师登录页面</p>
            <el-button type="success" class="btn-role btn-teacher" @click="navigateTo('/teacherLogin')">
              进入
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'

const router = useRouter()

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
:root {
  --primary-color: #3498db;
  --secondary-color: #2c3e50;
  --accent-color: #e74c3c;
  --muted: #f4f7fb;
}

/* 页面整体背景装饰 */
.welcome-page {
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 容器与点状装饰 */
.main-container {
  background-color: rgba(255, 255, 255, 0.95);
  border-radius: 15px;
  box-shadow: 0 12px 40px rgba(30, 40, 50, 0.12);
  overflow: hidden;
  max-width: 900px;
  width: 90%;
  margin: 2rem auto;
  padding: 2rem;
  text-align: center;
  position: relative;
}

.main-container::before,
.main-container::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  opacity: 0.12;
}
.main-container::before {
  width: 220px;
  height: 220px;
  right: -60px;
  top: -60px;
  background: radial-gradient(circle at 30% 30%, var(--primary-color), transparent 40%);
}
.main-container::after {
  width: 120px;
  height: 120px;
  left: -40px;
  bottom: -30px;
  background: radial-gradient(circle at 70% 70%, #2ecc71, transparent 40%);
}

/* logo */
.logo {
  margin-bottom: 1.5rem;
}
.logo-icon-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(52,152,219,0.9), rgba(46,204,113,0.85));
  box-shadow: 0 8px 30px rgba(52,152,219,0.18), inset 0 -6px 18px rgba(255,255,255,0.06);
  transform: translateY(0);
  transition: transform 0.35s cubic-bezier(.22,.9,.28,1), box-shadow 0.35s;
}
.logo-icon-wrapper:hover {
  transform: translateY(-8px) scale(1.03);
  box-shadow: 0 18px 40px rgba(52,152,219,0.22), inset 0 -8px 24px rgba(255,255,255,0.08);
}
.logo-icon {
  font-size: 3.2rem;
  color: #fff;
  filter: drop-shadow(0 6px 18px rgba(0,0,0,0.12));
  animation: float 4s ease-in-out infinite;
}

/* 浮动动画 */
@keyframes float {
  0% { transform: translateY(0) }
  50% { transform: translateY(-6px) }
  100% { transform: translateY(0) }
}

/* 标题与描述 */
.title {
  color: var(--secondary-color);
  font-weight: 700;
  margin-bottom: 1rem;
}
.description {
  color: #555;
  margin-bottom: 2rem;
  line-height: 1.8;
  font-size: 1.05rem;
}

/* 角色卡布局 */
.role-buttons {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin-top: 1rem;
}
.role-card {
  width: 220px;
  border: none;
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.35s cubic-bezier(.22,.9,.28,1), box-shadow 0.35s;
  background: linear-gradient(180deg, #ffffff 0%, var(--muted) 100%);
  box-shadow: 0 6px 22px rgba(30,40,50,0.08);
  position: relative;
}
.role-card:hover {
  transform: translateY(-12px);
  box-shadow: 0 20px 50px rgba(30,40,50,0.12);
}
.role-card::after {
  content: '';
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  right: 12px;
  top: 12px;
  background: radial-gradient(circle, rgba(255,255,255,0.9), rgba(255,255,255,0.2));
  box-shadow: 0 6px 18px rgba(0,0,0,0.06);
  opacity: 0.9;
}

/* 图标容器（卡片内） */
.role-icon-wrapper {
  width: 78px;
  height: 78px;
  margin: 0.4rem auto 1rem auto;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(52,152,219,0.12), rgba(46,204,113,0.09));
  box-shadow: 0 8px 20px rgba(30,40,60,0.06);
  transition: transform 0.3s, box-shadow 0.3s, background 0.3s;
}
.role-card:hover .role-icon-wrapper {
  transform: translateY(-6px) scale(1.04);
  box-shadow: 0 18px 40px rgba(30,40,60,0.08);
}
.role-icon {
  font-size: 1.8rem;
  color: var(--primary-color);
}

/* 卡片标题图标 */
.title-icon {
  margin-right: 8px;
  font-size: 1.2rem;
}

/* 卡片顶部色条 */
.student-card { border-top: 5px solid #3498db; }
.teacher-card { border-top: 5px solid #2ecc71; }

.card-body {
  padding: 1.25rem;
}
.card-title {
  font-weight: 600;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-text {
  color: #666;
  margin-bottom: 1rem;
}

/* 按钮样式 */
.btn-role {
  border-radius: 999px;
  padding: 0.5rem 1.4rem;
  font-weight: 600;
  transition: transform 0.22s, box-shadow 0.22s;
}
.btn-role:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 26px rgba(0,0,0,0.12);
}


/* 响应式 */
@media (max-width: 768px) {
  .role-buttons {
    flex-direction: column;
    align-items: center;
  }
  .role-card {
    width: 86%;
  }
  .main-container {
    padding: 1.5rem;
  }
}
</style>
