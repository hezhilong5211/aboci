# Aurora UI - 极光平台前端

基于 **Vite + Vue 3 + TypeScript + Arco Design** 的前端项目。

## 技术栈

- **Vue 3**: 渐进式 JavaScript 框架
- **TypeScript**: 类型安全的 JavaScript
- **Vite**: 下一代前端构建工具
- **Arco Design**: 字节跳动企业级 UI 设计语言
- **Vue Router**: 官方路由管理器
- **Pinia**: Vue 官方状态管理库
- **Axios**: HTTP 客户端

## 项目结构

```
aurora-ui/
├── src/
│   ├── api/              # API 接口定义
│   │   └── ai.ts         # AI 相关接口
│   ├── views/            # 页面组件
│   │   ├── ai/           # AI 模块
│   │   │   ├── apimanage/# 模型管理
│   │   │   ├── audit/    # 内容审核日志
│   │   │   └── chat/     # AI 对话
│   │   ├── system/       # 系统管理模块
│   │   │   ├── user/     # 用户管理
│   │   │   ├── role/     # 角色管理
│   │   │   └── menu/     # 菜单管理
│   │   ├── oss/          # 文件管理模块
│   │   │   └── file/     # 文件管理
│   │   └── login/        # 登录页面
│   ├── layout/           # 布局组件
│   │   └── index.vue     # 主布局（侧边栏 + 顶部 + 内容区）
│   ├── router/           # 路由配置
│   │   └── index.ts
│   ├── utils/            # 工具函数
│   │   └── request.ts    # Axios 封装
│   ├── App.vue           # 根组件
│   └── main.ts           # 入口文件
├── vite.config.ts        # Vite 配置
├── tsconfig.json         # TypeScript 配置
└── package.json          # 依赖管理
```

## 快速开始

### 安装依赖

```bash
npm install
# 或
yarn install
# 或
pnpm install
```

### 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
```

## 核心功能

### 1. 代理配置

所有 `/api` 开头的请求会自动代理到 `http://localhost:8080`（后端地址）。

### 2. AI 对话组件

- 支持 SSE 流式响应
- 实时打字机效果
- 消息历史记录
- 清空对话功能

### 3. 请求封装

- 自动携带 Token
- 统一错误处理
- 响应数据自动解包

## 开发规范

- 使用 TypeScript 进行类型检查
- 使用 Composition API (`<script setup>`)
- 遵循 Arco Design 设计规范
- 代码注释使用中文

## 注意事项

1. 确保后端服务运行在 `http://localhost:8080`
2. Token 存储在 `localStorage` 的 `token` 字段中
3. 后端接口统一返回 `Result<T>` 格式

