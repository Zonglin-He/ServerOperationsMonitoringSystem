// main.ts 或 main.js
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import axios from 'axios'

import '@/assets/css/element.less'
import 'flag-icons/css/flag-icons.min.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

// ✅ 关键：走同源相对路径，由 Vite 代理到 8080
axios.defaults.baseURL = ''
axios.defaults.withCredentials = true   // 若服务端用 Cookie，开着没问题
axios.defaults.timeout = 10000

import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 全量注册图标组件：Plus、Edit、CircleCheckFilled 等才能用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}


const pinia = createPinia()

app.use(pinia)
pinia.use(piniaPluginPersistedstate)
app.use(router)

app.mount('#app')
