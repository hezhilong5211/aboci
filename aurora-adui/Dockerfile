# ============================================================
# 极光前端镜像(多阶段:Node 构建 → nginx 托管静态 + 反代后端)
# 构建上下文 = aurora-adui 目录。
# ============================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci || npm install
COPY . .
# 不走 npm run build:其中的 vue-tsc 1.8 与锁定的 TypeScript 5.9 不兼容,启动即报错;直接用 vite 构建
RUN npx vite build

# ---- 运行阶段 ----
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
