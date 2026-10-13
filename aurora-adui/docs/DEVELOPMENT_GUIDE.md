# 前端开发指南 · Arco Design Vue 规范

> 本文合并自原 `DEVELOPMENT_GUIDE` / `ARCO_CONFIG` / `ARCO_COMPONENTS` 三份文档(去重),是前端开发的单一权威指南。

## 🎯 核心原则

**优先使用 Arco Design Vue 框架组件,99% 的 UI 需求都能用框架组件实现。**
开发任何 UI 前,**先访问** [Arco Design Vue 官方文档](https://arco.design/vue/docs/start) 搜索是否有现成组件,避免重复造轮子、保证设计一致。

## ⚙️ Arco 接入配置(项目已配好,了解即可)

项目已按 Arco 官方推荐配置好"按需自动导入 + 全局样式",**写组件无需手动 `import`**。三处配置:

```typescript
// 1. src/main.ts —— 全局引样式 + 全局注册
import '@arco-design/web-vue/dist/arco.css';
import ArcoVue from '@arco-design/web-vue';
app.use(ArcoVue);

// 2. vite.config.ts —— unplugin-vue-components 按需自动导入
import Components from 'unplugin-vue-components/vite';
import { ArcoResolver } from 'unplugin-vue-components/resolvers';
export default defineConfig({
  plugins: [
    Components({ resolvers: [ ArcoResolver({ sideEffect: true /* 自动导入样式 */ }) ] }),
  ],
});
```

效果:组件直接 `<a-button>` 用、样式自动加载、按需打包(未用组件不进包)。
验证:任意 `.vue` 直接写 `<a-button>` 能渲染即生效;自动生成的 `components.d.ts` 里能看到 Arco 组件类型。

## 🚫 禁止 vs ✅ 可自定义

**❌ 以下基础组件必须用 Arco,禁止自己实现**(发现自己在造这些,立即停手查文档):
按钮 / 输入框 / 选择器 / 开关 · 表格 / 列表 / 卡片 / 树 · 对话框 / 抽屉 / 提示框 / 确认框 · 表单 / 表单验证 · 菜单 / 面包屑 / 标签页 / 分页 · 加载 / 空状态 / 骨架屏 · 上传 / 日期 / 时间选择 · 消息 / 通知 · 徽标 / 标签 / 头像。

**✅ 可以自定义的**:业务逻辑封装(组合多个 Arco 组件)、特定业务场景的复合组件、页面级业务组件。

## ✅ 开发流程

1. **需求分析**:明确 UI 需求(要表格 / 表单 / 对话框…)。
2. **查文档**:访问 [Arco 组件文档](https://arco.design/vue/docs/start),看对应组件的 API(Props/Events/Slots)、示例、最佳实践。
3. **用框架组件**:直接使用,无需导入。
4. **样式定制(如需)**:优先用组件的 Props 和插槽,**最后**才考虑自定义 CSS。

```vue
<!-- ✅ 优先 Props + 插槽 -->
<a-button type="primary" size="large">按钮</a-button>
<a-card>
  <template #title>标题</template>
  <template #extra><a-button>操作</a-button></template>
</a-card>
```

## 📖 常用组件示例

```vue
<!-- 表格 https://arco.design/vue/component/table -->
<a-table :columns="columns" :data="data" :pagination="pagination" @page-change="handlePageChange" />

<!-- 表单 https://arco.design/vue/component/form -->
<a-form :model="form" :rules="rules" @submit="handleSubmit">
  <a-form-item field="name" label="姓名"><a-input v-model="form.name" /></a-form-item>
  <a-form-item><a-button type="primary" html-type="submit">提交</a-button></a-form-item>
</a-form>

<!-- 对话框 https://arco.design/vue/component/modal -->
<a-modal v-model:visible="visible" title="标题" @ok="handleOk" @cancel="handleCancel"><p>内容</p></a-modal>

<!-- 下拉选择 https://arco.design/vue/component/select -->
<a-select v-model="value" :options="options" placeholder="请选择" />

<!-- 日期 https://arco.design/vue/component/date-picker -->
<a-date-picker v-model="date" format="YYYY-MM-DD" />
```

```typescript
// 消息提示 https://arco.design/vue/component/message
import { Message } from '@arco-design/web-vue';
Message.success('操作成功');
```

## 🗂️ Arco 完整组件清单

(⭐ = 项目当前已在用)

- **基础**:Button(`a-button`)、Icon(`icon-*`)、Typography/Title/Text/Paragraph、Link、Divider、Space、Grid(`a-row`/`a-col`)
- **布局**:Layout(`a-layout`)、Header/Sider/Content/Footer
- **导航**:Menu(`a-menu`)⭐、SubMenu、Breadcrumb、Tabs、Pagination、Steps、Anchor、Dropdown(`a-dropdown`)⭐
- **数据输入**:Input(`a-input`)、Input.Search ⭐、Input.Password、InputNumber、Textarea、Select、Cascader、TreeSelect、Checkbox、Radio、Switch、Slider、Rate、DatePicker、TimePicker、Upload、Form(`a-form`)⭐、Form.Item、Mention
- **数据展示**:Table(`a-table`)、List、Card(`a-card`)⭐、Collapse、Timeline、Tree、Badge ⭐、Tag、Avatar ⭐、Empty、Descriptions、Image、Carousel、Statistic、Skeleton
- **反馈**:Message ⭐、Notification、Modal、Drawer、Popconfirm、Tooltip、Popover、Progress、Alert、Spin、Result
- **其他**:BackTop、Affix、Calendar、ColorPicker、Trigger、ResizeBox、Split、Scrollbar

完整 API 以 [Arco 官方文档](https://arco.design/vue/docs/start) 为准(顶部搜索框搜功能关键词)。

## 🎨 图标使用

```vue
<template>
  <icon-user />            <!-- 方式1:自动导入,直接用(推荐) -->
  <component :is="IconUser" />  <!-- 方式2:组件方式 -->
</template>
<script setup lang="ts">
import { IconUser } from '@arco-design/web-vue/es/icon';
</script>
```

## 🔍 组件查找

| 功能需求 | 组件分类 | 代表组件 |
|---|---|---|
| 布局 | Layout | `a-layout` / `a-row` / `a-col` |
| 导航 | Navigation | `a-menu` / `a-tabs` / `a-breadcrumb` |
| 表单输入 | Data Entry | `a-form` / `a-input` / `a-select` |
| 数据展示 | Data Display | `a-table` / `a-card` / `a-list` |
| 用户反馈 | Feedback | `a-modal` / `Message` / `a-notification` |

## 💡 最佳实践

- **组合组件**:用 `a-card` + `a-table` + `a-pagination` 等组合实现复杂页面。
- **善用插槽**:`#title` / `#extra` 等插槽定制,而非堆 CSS。
- **TypeScript 类型**:为 Props 加类型,如 `const columns: TableColumnData[] = [...]`(`import type { TableColumnData } from '@arco-design/web-vue'`)。

## ✅ 开发检查清单

- [ ] 已查阅 Arco 官方文档
- [ ] 用了合适的 Arco 组件,没重复实现已有组件
- [ ] 用了自动导入(无需手动 `import` 组件)
- [ ] 遵循 Arco 设计规范、通过 TypeScript 类型检查

## 🌊 AI 流式对话(SSE)实现

后端 AI 对话走 SSE 流式,**禁用 axios**(对流式支持差),用原生 `fetch + getReader`:

```typescript
const resp = await fetch(
  `/api/ai/stream?message=${encodeURIComponent(msg)}`,
  { headers: { Authorization: `Bearer ${token}` } },
);
const reader = resp.body!.getReader();
const decoder = new TextDecoder();
while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  for (const line of decoder.decode(value, { stream: true }).split('\n\n')) {
    if (line.startsWith('data:')) {
      streamingText.value += line.slice(5); // 后端每块是一段纯文本
    }
  }
}
```

> 实际端点:`/api/ai/stream`(对应后端 `GET /ai/stream`)。**没有** `/api/ai/chat/stream` 这个端点。

## 🧩 前端编码约定(补充)

- `<script setup lang="ts">`;基本类型用 `ref` 而非 `reactive`。
- 显式定义接口类型:API 请求/响应、组件 Props、Store state。
- 注释 / 文档 / 用户文案用**中文**;组件私有样式 `<style scoped lang="less">`。
- API 统一经 `@/utils/request`(单例,`/api` 前缀由 vite 代理加);响应 `Result<T>`(code/data/msg),`code !== 200` 为业务错误,用 `Message` 提示。

## 🐛 遇到问题

1. **组件找不到 / 不知道用哪个?** → 查上方组件清单,或 [Arco 文档](https://arco.design/vue/docs/start) 搜功能关键词。
2. **样式不符需求?** → 优先 Props + 插槽,最后才自定义 CSS。
3. **需要自定义组件?** → 确保是业务逻辑封装,而非基础 UI 组件。

## 📚 资源

- [Arco Design Vue 官方文档](https://arco.design/vue/docs/start)
- [Arco Design 设计指南](https://arco.design/docs/spec/introduce)
- [Arco Design Pro Vue](https://arco.design/pro/vue)

---

**记住:Arco Design 组件库异常丰富,优先使用框架组件!**
