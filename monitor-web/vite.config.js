// vite.config.ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
    plugins: [
        vue(),
        AutoImport({ resolvers: [ElementPlusResolver()] }),
        Components({ resolvers: [ElementPlusResolver()] }),
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    // ★ 新增：开发代理到后端 8080
    server: {
        port: 5173, // 可省略，默认也是 5173
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                // 如果后端不带 /api 前缀才需要：
                // rewrite: path => path.replace(/^\/api/, '')
            },
            '/terminal': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true // 终端若走 WebSocket，顺便代理
            }
        }
    }
})
