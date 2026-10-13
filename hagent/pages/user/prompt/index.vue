<template>
	<view class="prompt-page">
		<!-- 自定义导航栏 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">个性化提示词</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<!-- 内容区域 -->
		<view class="content" :style="{ paddingTop: (statusBarHeight + 44) + 'px' }">
			<!-- 顶部说明卡 -->
			<view class="tip-card">
				<view class="tip-icon">
					<text class="tip-icon-text">i</text>
				</view>
				<text class="tip-text">这段提示词会叠加进每次对话（全局 + Skill + <text class="tip-highlight">个性化</text>），仅对你本人生效。</text>
			</view>

			<!-- 提示词输入卡 -->
			<view class="editor-card">
				<textarea
					v-model="content"
					class="editor-textarea"
					placeholder="请输入你的个性化提示词，例如：我是一名连锁店的资深发型师，回答请优先给可落地的造型/染发方案……"
					placeholder-class="editor-placeholder"
					:maxlength="MAX_LENGTH"
					:disabled="loading"
				/>
				<text class="editor-counter">{{ content.length }}/{{ MAX_LENGTH }}</text>
			</view>

			<!-- 保存按钮 -->
			<button class="save-btn" :class="{ disabled: saving }" :disabled="saving" @tap="handleSave">
				保存，即刻生效
			</button>
		</view>
	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getUserPrompt, saveUserPrompt } from '@/api/agent.js'

// 字数上限（与设计稿 22 一致）
const MAX_LENGTH = 500

const content = ref('')
const loading = ref(false)
const saving = ref(false)

// 获取状态栏高度
const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	}
})

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

/** 拉取用户已保存的提示词并回填 */
async function loadPrompt() {
	loading.value = true
	try {
		const data = await getUserPrompt()
		// 后端可能返回字符串或含 content 字段的对象，做兼容处理
		const text = typeof data === 'string' ? data : (data?.content || '')
		content.value = (text || '').slice(0, MAX_LENGTH)
	} catch (e) {
		// 错误已由 request 统一处理
	} finally {
		loading.value = false
	}
}

/** 保存提示词 */
async function handleSave() {
	if (saving.value) return
	saving.value = true
	try {
		await saveUserPrompt(content.value.trim())
		uni.showToast({ title: '保存成功', icon: 'success' })
	} catch (e) {
		// 错误已由 request 统一处理
	} finally {
		saving.value = false
	}
}

onLoad(() => {
	loadPrompt()
})
</script>

<style lang="scss" scoped>
.prompt-page {
	min-height: 100vh;
	background-color: #F6F7FB;
}

/* ====== 自定义导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #F6F7FB;
}

.nav-inner {
	height: 44px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 16rpx;
}

.nav-back {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 52rpx;
	color: #0F172A;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #0F172A;
}

.nav-placeholder {
	width: 72rpx;
}

/* ====== 内容区域 ====== */
.content {
	box-sizing: border-box;
	padding-left: 36rpx;
	padding-right: 36rpx;
}

/* ====== 顶部说明卡 ====== */
.tip-card {
	display: flex;
	align-items: flex-start;
	gap: 16rpx;
	padding: 24rpx 28rpx;
	margin-top: 28rpx;
	border-radius: 24rpx;
	background-color: #DEEAFB;
	border: 1rpx solid rgba(96, 140, 224, 0.15);
}

.tip-icon {
	flex: none;
	width: 32rpx;
	height: 32rpx;
	margin-top: 2rpx;
	border-radius: 50%;
	border: 3rpx solid #4F84EA;
	display: flex;
	align-items: center;
	justify-content: center;
}

.tip-icon-text {
	font-size: 22rpx;
	font-weight: 700;
	font-style: italic;
	color: #4F84EA;
	line-height: 1;
}

.tip-text {
	flex: 1;
	font-size: 24rpx;
	line-height: 1.6;
	color: #64748B;
}

.tip-highlight {
	color: #4F84EA;
	font-weight: 600;
}

/* ====== 提示词输入卡 ====== */
.editor-card {
	position: relative;
	margin-top: 32rpx;
	padding: 32rpx;
	padding-bottom: 64rpx;
	border-radius: 32rpx;
	background-color: #FFFFFF;
	border: 1rpx solid rgba(96, 140, 224, 0.25);
	box-shadow: 0 4rpx 20rpx rgba(96, 140, 224, 0.05);
}

.editor-textarea {
	width: 100%;
	min-height: 360rpx;
	font-size: 30rpx;
	line-height: 1.8;
	color: #1E293B;
}

.editor-placeholder {
	color: #CBD5E1;
	font-size: 30rpx;
	line-height: 1.8;
}

.editor-counter {
	position: absolute;
	right: 32rpx;
	bottom: 28rpx;
	font-size: 24rpx;
	color: #CBD5E1;
}

/* ====== 保存按钮 ====== */
.save-btn {
	width: 100%;
	height: 104rpx;
	line-height: 104rpx;
	margin-top: 40rpx;
	border-radius: 30rpx;
	border: none;
	background: linear-gradient(135deg, #4F84EA, #8FB4FB);
	color: #FFFFFF;
	font-size: 32rpx;
	font-weight: 700;
	box-shadow: 0 16rpx 48rpx rgba(96, 140, 224, 0.28);

	&::after {
		border: none;
	}

	&.disabled {
		opacity: 0.6;
	}
}
</style>
