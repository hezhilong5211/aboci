import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // 微信工具关闭二次转译后，由 Vite 统一转换现代语法，开发和发行保持一致。
  build: process.env.UNI_PLATFORM === 'mp-weixin' ? { target: 'es2015' } : {},
})
