import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'



// 创建应用实例
const app = createApp(App)


// 使用插件
app.use(router)
app.use(ElementPlus)
// 挂载应用
app.mount('#app')