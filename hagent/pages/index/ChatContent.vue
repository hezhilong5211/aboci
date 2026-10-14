<template>
	<view class="chat-content">
		<!-- 空会话视觉层独立于消息滚动区，不参与键盘动画。 -->
		<view
			v-if="!hasMessages"
			class="empty-state"
		>
			<text class="empty-kicker">AI 美业创作</text>
			<view class="empty-title">
				<text class="empty-line">今天，</text>
				<text class="empty-line">想创作些<text class="empty-accent">什么</text>？</text>
			</view>
			<text class="empty-sub">发图、提问、出图 —— 一句话开始。</text>
		</view>

		<!-- 主体滚动区 -->
		<scroll-view
			class="page-scroll"
			scroll-y
			:show-scrollbar="false"
			:scroll-into-view="scrollIntoView"
			:scroll-top="controlledScrollTop"
			:scroll-with-animation="scrollWithAnimation"
			@scroll="onPageScroll"
		>
			<view class="scroll-anchor" :style="{ paddingTop: topSpace, paddingBottom: scrollPadBottom }">
				<!-- 消息流 -->
				<view class="msg-list" v-if="hasMessages">
					<view v-if="historyOlderLoading" class="history-older-loading">
						<text class="history-older-loading-text">正在加载更早消息…</text>
					</view>
					<view
						class="msg-row"
						:id="messageDomId(msg, mIdx)"
						v-for="(msg, mIdx) in messages"
						:key="msg.renderKey || msg.id || mIdx"
					>
						<!-- 用户气泡(右) -->
							<view v-if="msg.role === 'user'" class="user-row">
								<view class="user-bubble">
									<!-- 用户附带的图片 -->
									<view class="user-imgs" v-if="msg.images && msg.images.length">
										<view class="user-img-wrap" v-for="(img, i) in msg.images" :key="i">
											<image
												class="user-img"
												:src="fullUrl(img)"
												mode="aspectFill"
												@tap="previewUserImg(msg.images, i)"
											/>
											<text class="user-img-index">图{{ i + 1 }}</text>
										</view>
									</view>
									<text
										v-if="msg.content"
										class="user-text am-selectable-text"
										:class="{ 'user-text--collapsed': msg.collapsible && !msg.expanded }"
										user-select
									>{{ msg.content }}</text>
									<view class="user-actions" v-if="msg.content">
										<text v-if="msg.collapsible" class="user-action" @tap="msg.expanded = !msg.expanded">
											{{ msg.expanded ? '收起' : '展开' }}
										</text>
										<text class="user-action" @tap="favoriteUserMessage(msg)">收藏</text>
										<text class="user-action" @tap="copyUserMessage(msg)">复制</text>
										<text v-if="canEditMessage" class="user-action" @tap="editUserMessage(msg)">编辑</text>
									</view>
								</view>
						</view>

						<!-- AI 块(左):去逐条头像,整行铺满 -->
						<view v-else class="ai-row">
							<view class="ai-body">
								<!-- 过程条:思考 + 工具合并 -->
								<ProcessStrip
									:queue-message="msg.queueMessage"
									:thinking="msg.thinking"
									:tools="msg.tools"
									:streaming="(streaming || recoveringConversation) && mIdx === messages.length - 1"
									:has-result="!!msg.result"
								/>

								<!-- 流式正文:流式中该条纯文本直显(避免逐帧全量解析 markdown 卡顿),完成/历史消息用 mp-html 渲染 markdown -->
								<text v-if="msg.text && !msg.clarification && isStreamingMsg(mIdx)" class="ai-text am-selectable-text" user-select>{{ msg.text }}</text>
								<mp-html
									v-else-if="msg.text && !msg.clarification"
									class="ai-md"
									:content="renderMarkdown(msg.text)"
									:tag-style="mdTagStyle"
									selectable="force"
									@load="onHistoryMarkdownLoad"
								/>

								<ClarificationCard
									v-if="msg.clarification"
									:clarification="msg.clarification"
									:active="isActiveClarification(msg.clarification)"
									@select="selectClarificationOption(msg, $event)"
									@new-task="startNewTaskFromClarification"
								/>

								<view v-if="msg.rechargeRequired && msg.rechargeRequestId === conversationStore.currentSession.requestId" class="recharge-recovery">
									<text class="recharge-recovery-hint">{{ msg.sourceMessageId != null ? '充值到账后可继续这条任务' : '充值后请重新发送需求' }}</text>
									<text class="recharge-recovery-action" @tap="goRecharge">去充值</text>
								</view>

								<!-- 结果分段卡 -->
									<ResultCard
										v-if="msg.result"
										:result="msg.result"
										@add-reference="addGeneratedReference"
										@retry-image="retryFailedSlot(msg.result, $event)"
									/>

									<view v-if="msg.stopped" class="ai-stopped">
										<text class="ai-stopped-title">已停止</text>
										<text v-if="msg.result && msg.result.images && msg.result.images.length" class="ai-stopped-sub">
											已提交图片继续生成
										</text>
									</view>

								<!-- 既无文本也无思考也无工具也无结果且仍在流式:等待提示 -->
								<view
									v-if="isWaiting(msg, mIdx)"
									class="ai-waiting"
								>
									<view class="ai-waiting-dot" />
									<view class="ai-waiting-dot" />
									<view class="ai-waiting-dot" />
								</view>
							</view>
						</view>
					</view>
				</view>
				<view id="chat-scroll-bottom" class="scroll-bottom-anchor" />
			</view>
		</scroll-view>

		<!-- 底部固定输入区(浮在 tabBar 之上) -->
		<view class="dock" :style="dockStyle">
			<view v-if="pendingClarification" class="pending-clarification-bar">
				<view class="pending-clarification-copy">
					<text class="pending-clarification-title">正在回答刚才的问题</text>
					<text class="pending-clarification-question">{{ pendingClarification.question }}</text>
				</view>
				<text class="pending-clarification-cancel" @tap="startNewTaskFromClarification">开始新任务</text>
			</view>
			<!-- 单 Skill 状态芯片:仅激活时显示(激活入口在 + 弹窗),点击快捷取消激活 -->
			<view
				class="skill-chip"
				:class="{ 'skill-chip--active': skillActive }"
				v-if="skill && skillActive"
				@tap="toggleSkill"
			>
				<text class="skill-chip-star">✦</text>
				<text class="skill-chip-name">{{ skill.name }}</text>
				<text class="skill-chip-state" v-if="skillActive">已激活</text>
			</view>

			<!-- 已附带图片缩略图(对应屏 05b) -->
			<scroll-view class="dock-imgs" v-if="pendingImages.length" scroll-x :show-scrollbar="false">
					<view class="dock-img-track">
						<view class="dock-img" v-for="(img, i) in pendingImages" :key="i">
						<image
							class="dock-img-el"
							:src="fullUrl(img)"
							mode="aspectFill"
							@tap="previewPendingImg(i)"
							@longpress.stop="openImageOrderEditor"
						/>
							<view class="dock-img-del" @tap.stop="removeImage(i)">
								<text class="dock-img-del-x">×</text>
							</view>
							<text class="dock-img-index">图{{ i + 1 }}</text>
						</view>
				</view>
			</scroll-view>

			<view class="chat-models" v-if="userStore.isLoggedIn">
				<view class="chat-model-slot">
					<ModelSelector compact label="对话" :models="chatModels" :model-value="conversationStore.currentSession.modelId" :disabled="interactionLocked" :loading="modelsLoading" @update:model-value="selectChatModel" />
				</view>
				<view class="chat-model-slot">
					<ModelSelector compact label="出图" :models="imageModels" :model-value="conversationStore.currentSession.imageModelId" :disabled="interactionLocked" :loading="modelsLoading" @update:model-value="selectImageModel" />
				</view>
			</view>
			<!-- 编辑器:上传/文字/发送三段固定分区,文字增高不改变按钮位置与可用宽度 -->
			<view
				class="input-row"
				:class="{ 'input-row--focused': inputFocused }"
			>
				<view class="input-plus-wrap">
					<!-- + 号自定义弹窗:锚定独立上传按钮,不再嵌套在原生输入框内 -->
					<InputPlusPopup
						:visible="plusVisible"
						:skill-name="skill ? skill.name : ''"
						:skill-active="skillActive"
						@close="onPlusClose"
						@upload="onPlusUpload"
						@toggle-skill="onPlusToggleSkill"
					/>
					<view class="input-plus" @tap.stop="openPlus">
						<view class="input-plus-surface">
							<text class="input-plus-icon">＋</text>
						</view>
					</view>
				</view>

				<!-- 只测量已写入 value 的真实文本,未上屏拼音不参与外框高度 -->
				<view
					class="input-surface"
					:style="{ height: inputSurfaceHeight }"
				>
					<text class="input-measure">{{ inputMeasureText }}</text>
					<textarea
						class="input-field"
						:style="{ height: inputSurfaceHeight }"
						:value="inputText"
						:placeholder="preparingConversation ? '正在准备…' : interactionLocked ? '任务后台处理中…' : '描述你的需求…'"
						placeholder-class="input-ph"
						:disabled="interactionLocked"
						:maxlength="-1"
						:adjust-position="false"
						:fixed="true"
						:show-confirm-bar="false"
						:disable-default-padding="true"
						@input="onInputChange"
						@focus="onInputFocus"
						@blur="onInputBlur"
						@keyboardheightchange="onKeyboardHeightChange"
					/>
				</view>

				<!-- 流式中:红色停止;否则按有无输入切灰/渐变发送 -->
				<view
					v-if="showStopButton"
					class="send-btn send-btn--stop"
					@tap.stop="handleStop"
				>
					<view class="send-btn-surface">
						<view class="stop-square" />
					</view>
				</view>
				<view
					v-else
					class="send-btn"
					:class="{ 'send-btn--active': canSend }"
					@tap.stop="handleSend"
				>
					<view class="send-btn-surface">
						<text class="send-icon">↑</text>
					</view>
				</view>
			</view>
		</view>

		<ImageOrderEditor
			:visible="orderEditorVisible"
			:urls="pendingImageUrls"
			@cancel="orderEditorVisible = false"
			@confirm="applyPendingImageOrder"
		/>

		<!-- 历史浮层 -->
		<HistoryOverlay
			:visible="historyVisible"
			:navigation-metrics="navigationMetrics"
			:conversations="conversations"
			:loading="historyLoading"
			:switching-conversation-id="historySwitchingId"
			:creating="newConversationSwitching"
			:closing="historyClosing"
			@close="closeHistory"
			@create-new="onHistoryNew"
			@pick="onHistoryPick"
		/>
	</view>
</template>

<script setup>
import { computed, ref, nextTick, onMounted, onBeforeUnmount, getCurrentInstance, watch } from 'vue'
import config from '@/utils/config.js'
import { useConversationStore } from '@/store/conversation.js'
import { useAccountStore } from '@/store/account.js'
import { useUserStore } from '@/store/user.js'
import { useLoginGuard } from '@/composables/useLoginGuard.js'
import {
	chatStream,
	resumeChatStream,
	createConversation,
	getGeneration,
	listConversations,
	getMessagePage,
	getPendingClarification,
	getConversationRun,
	getAvailableModels,
	getSkills,
	createPromptFavorite,
	retryFailedImage,
	stopChat,
} from '@/api/agent.js'
import { getBalance } from '@/api/account.js'
import { uploadFile } from '@/api/common.js'
import ProcessStrip from './components/ProcessStrip.vue'
import ResultCard from './components/ResultCard.vue'
import ClarificationCard from './components/ClarificationCard.vue'
import InputPlusPopup from './components/InputPlusPopup.vue'
import HistoryOverlay from './components/HistoryOverlay.vue'
import ModelSelector from '@/components/common/ModelSelector.vue'
import ImageOrderEditor from '@/components/common/ImageOrderEditor.vue'
import { renderMarkdown } from '@/utils/markdown.js'

const props = defineProps({
	navigationMetrics: {
		type: Object,
		default: () => ({
			statusBarHeight: 20,
			navigationBarHeight: 44,
			rightSafeWidth: 96,
		}),
	},
})

// mp-html 渲染 AI markdown 的标签样式:表格描边、代码块、引用条
const mdTagStyle = {
	table: 'border-collapse:collapse;width:100%;margin:12rpx 0;font-size:26rpx',
	th: 'border:1rpx solid #dfe3ea;padding:10rpx 14rpx;background:#f4f7fc;font-weight:600;text-align:left',
	td: 'border:1rpx solid #dfe3ea;padding:10rpx 14rpx',
	pre: 'background:#f4f7fc;border-radius:12rpx;padding:16rpx;overflow:auto;font-size:24rpx',
	code: 'background:#f0f2f5;border-radius:6rpx;padding:2rpx 8rpx;font-size:24rpx',
	blockquote: 'border-left:6rpx solid #c7d4ea;padding-left:20rpx;color:#64748b;margin:12rpx 0',
}

const conversationStore = useConversationStore()
const accountStore = useAccountStore()
const userStore = useUserStore()
const { requireLogin } = useLoginGuard()
const componentInstance = getCurrentInstance()

// ====== 直接消费 store 状态 ======
const messages = computed(() => conversationStore.messages)
const streaming = computed(() => conversationStore.streaming)
const recoveringConversation = computed({
	get: () => conversationStore.currentSession.recovering,
	set: (value) => { conversationStore.currentSession.recovering = value },
})
const preparingConversation = ref(false)
const interactionLocked = computed(
	() => streaming.value || recoveringConversation.value || preparingConversation.value
		|| conversationStore.currentSession.loadingHistory === true
)
const showStopButton = computed(
	() => streaming.value || (recoveringConversation.value && conversationStore.activeMessageId != null)
)
const hasMessages = computed(() => messages.value.length > 0)

const chatModels = ref([]), imageModels = ref([]), modelsLoading = ref(false)
let modelsLoadVersion = 0
function ensureModelSelection() {
	if (modelsLoading.value || (!chatModels.value.length && !imageModels.value.length)) return
	const session = conversationStore.currentSession
	const choose = (list, id) => list.find((m) => sameId(m.modelId, id))?.modelId || list.find((m) => m.isDefault === 1 || m.isDefault === true)?.modelId || list[0]?.modelId || null
	conversationStore.setModels(choose(chatModels.value, session.modelId), choose(imageModels.value, session.imageModelId))
}
function selectChatModel(id) { conversationStore.setModels(id, conversationStore.currentSession.imageModelId) }
function selectImageModel(id) { conversationStore.setModels(conversationStore.currentSession.modelId, id) }
async function loadModels() {
	if (!userStore.isLoggedIn) return
	const epoch = sessionEpoch, version = ++modelsLoadVersion
	modelsLoading.value = true
	try {
		const data = await getAvailableModels()
		if (disposed || epoch !== sessionEpoch || version !== modelsLoadVersion) return
		chatModels.value = data?.chatModels || []
		imageModels.value = data?.imageModels || []
	} catch { /* Leave existing selection intact; the server validates availability. */ }
	finally {
		if (!disposed && epoch === sessionEpoch && version === modelsLoadVersion) {
			modelsLoading.value = false
			ensureModelSelection()
		}
	}
}

// ====== 顶部留白 / 底部定位 ======
// 顶部导航由首页页面壳负责，消息区只保留轻量呼吸空间。
const topSpace = '40rpx'
const windowInfo =
	typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
const safeAreaBottomPx =
	windowInfo.safeArea && windowInfo.screenHeight
		? Math.max(0, windowInfo.screenHeight - windowInfo.safeArea.bottom)
		: 0
const idleDockBottomPx = uni.upx2px(140) + safeAreaBottomPx
const VISIBLE_KEYBOARD_GAP_PX = 5
const LAST_MESSAGE_GAP_PX = 5
// 小屏+键盘+10行输入时 Dock 顶边可能被挤进导航区甚至为负,补偿滚动按此下限钳制,避免一次性异常大滚
const DOCK_TOP_MIN_PX = 100
const viewportPx = Math.max(0, Number(windowInfo.windowHeight) || 0)
// Dock 跟随动画只在 iOS 启用:iOS 的 keyboardheightchange 在键盘动画起点触发且带 duration,可并行滑动;
// 安卓该事件本身滞后于键盘出现,再加动画只会显得更慢,直接落位。
const deviceInfo =
	typeof uni.getDeviceInfo === 'function' ? uni.getDeviceInfo() : uni.getSystemInfoSync()
const isIOS = String(deviceInfo.platform || '').toLowerCase() === 'ios'
const inputFocused = ref(false)
const keyboardHeightPx = ref(0)
const dockTransitionMs = ref(0)
const scrollInsetBottomPx = ref(idleDockBottomPx)
let scrollInsetTimer = null
let keyboardCloseFallbackTimer = null
// 目标位置钳制 ≥ 常驻位:iPad 悬浮/分体键盘等会上报很小的高度,不钳制会让 Dock 固化时坠到常驻位之下
const dockTargetBottomPx = computed(() =>
	keyboardHeightPx.value > 0
		? Math.max(keyboardHeightPx.value + VISIBLE_KEYBOARD_GAP_PX, idleDockBottomPx)
		: idleDockBottomPx
)
// Dock 定位状态机:动画期用固定基准 bottom + transform(合成层,不逐帧重排);动画结束后
// 固化为纯 bottom + transform:none。同层渲染的原生 textarea 在带 transform 的祖先里,
// "点击坐标→光标落点"换算不补偿位移(点击定位跑到末尾),编辑期必须处于无 transform 的固化态。
// 动画只加在 Dock 容器上,原生 textarea 本身不参与 CSS 动画(否则 placeholder 闪动、光标错位)。
const dockAnimating = ref(false)
const dockRenderShiftPx = ref(0)
let dockMotionVersion = 0
let dockStartTimer = null
let dockSettleTimer = null
const dockStyle = computed(() =>
	dockAnimating.value
		? {
				bottom: `${idleDockBottomPx}px`,
				transform: `translate3d(0, -${dockRenderShiftPx.value}px, 0)`,
				transitionDuration: `${dockTransitionMs.value}ms`,
		  }
		: {
				bottom: `${dockTargetBottomPx.value}px`,
				transform: 'none',
				transitionDuration: '0ms',
		  }
)

function clearDockMotionTimers() {
	if (dockStartTimer) {
		clearTimeout(dockStartTimer)
		dockStartTimer = null
	}
	if (dockSettleTimer) {
		clearTimeout(dockSettleTimer)
		dockSettleTimer = null
	}
}

// 静止态起步需要两帧:先以 0 时长切到等价 transform 形态(与固化态视觉同位),下一帧再启动过渡。
// 32ms 留双帧余量:逻辑层定时器感知不到渲染层进度,间隔太短两帧可能被合并、动画退化为瞬移。
const DOCK_START_FRAME_MS = 32

function animateDockTo(previousShiftPx, nextShiftPx, durationMs) {
	const version = ++dockMotionVersion
	clearDockMotionTimers()
	if (durationMs <= 0) {
		dockAnimating.value = false
		dockTransitionMs.value = 0
		dockRenderShiftPx.value = nextShiftPx
		return
	}
	if (!dockAnimating.value) {
		dockAnimating.value = true
		dockTransitionMs.value = 0
		dockRenderShiftPx.value = previousShiftPx
		dockStartTimer = setTimeout(() => {
			dockStartTimer = null
			if (version !== dockMotionVersion) return
			dockTransitionMs.value = durationMs
			dockRenderShiftPx.value = nextShiftPx
			scheduleDockSettle(version, durationMs)
		}, DOCK_START_FRAME_MS)
		return
	}
	// 动画中改目标(合帧二次修正):CSS 过渡自动从当前位置重定向
	dockTransitionMs.value = durationMs
	dockRenderShiftPx.value = nextShiftPx
	scheduleDockSettle(version, durationMs)
}

function scheduleDockSettle(version, durationMs) {
	dockSettleTimer = setTimeout(() => {
		dockSettleTimer = null
		if (version !== dockMotionVersion) return
		// 固化:bottom=目标值 + transform:none,与动画终点视觉同位,一次切换。
		// 60ms 余量防高负载下渲染层过渡尚未走完就被固化截断尾段。
		dockAnimating.value = false
		dockTransitionMs.value = 0
	}, durationMs + 60)
}
const KEYBOARD_SCROLL_SETTLE_MS = 250
const KB_DEFAULT_ANIM_MS = 250
const KB_ANIM_MAX_MS = 400
// 聚焦初期的 height=0 是 iOS 弹起过渡期噪声:误当收起会让 Dock 先坠到键盘后、正值到达再弹回,跳变更大
const KB_ZERO_GRACE_MS = 600
const KB_COLLAPSE_CONFIRM_MS = 180
// 第三方输入法(搜狗/百度)一次聚焦连发多次高度回调且中间值可能是错的,短窗合帧只保留末值
const KB_EVENT_COALESCE_MS = 100

function clearScrollInsetTimer() {
	if (!scrollInsetTimer) return
	clearTimeout(scrollInsetTimer)
	scrollInsetTimer = null
}

function clearKeyboardCloseFallback() {
	if (!keyboardCloseFallbackTimer) return
	clearTimeout(keyboardCloseFallbackTimer)
	keyboardCloseFallbackTimer = null
}

// 最近一次聚焦时刻:为 iOS 弹起过渡期的 0 值噪声提供宽限判断基准
let focusAt = 0
let kbCollapseTimer = null
let kbCoalesceTimer = null
let kbPendingHeight = null
let kbPendingDurationMs = 0

function clearKbCollapseTimer() {
	if (!kbCollapseTimer) return
	clearTimeout(kbCollapseTimer)
	kbCollapseTimer = null
}

function clearKbCoalesce() {
	if (kbCoalesceTimer) {
		clearTimeout(kbCoalesceTimer)
		kbCoalesceTimer = null
	}
	kbPendingHeight = null
}

// 微信 keyboardheightchange 的 duration 单位是秒(iOS 典型 0.25);安卓不做动画,恒 0
function normalizeKbDurationMs(raw) {
	if (!isIOS) return 0
	const seconds = Number(raw)
	if (!Number.isFinite(seconds) || seconds <= 0) return KB_DEFAULT_ANIM_MS
	return Math.min(Math.round(seconds * 1000), KB_ANIM_MAX_MS)
}

// 正高度统一走短窗合帧:首个值立即生效(与 iOS 键盘动画同起点),窗口内后续值只保留最后一个,
// 吸收第三方输入法连发的错误中间高度,避免 Dock 连跳。
function scheduleKeyboardRaise(height, durationMs) {
	if (kbCoalesceTimer) {
		kbPendingHeight = height
		kbPendingDurationMs = durationMs
		return
	}
	applyKeyboardHeight(height, durationMs)
	kbCoalesceTimer = setTimeout(() => {
		kbCoalesceTimer = null
		if (kbPendingHeight == null) return
		const pendingHeight = kbPendingHeight
		const pendingDurationMs = kbPendingDurationMs
		kbPendingHeight = null
		applyKeyboardHeight(pendingHeight, pendingDurationMs)
	}, KB_EVENT_COALESCE_MS)
}

// 收起统一入口:丢弃未落地的抬起合帧值,避免收起后又被残留正值弹回
function collapseDock(durationMs) {
	clearKbCoalesce()
	clearKbCollapseTimer()
	applyKeyboardHeight(0, durationMs)
}

function onInputFocus(e) {
	clearKeyboardCloseFallback()
	clearKbCollapseTimer()
	inputFocused.value = true
	focusAt = Date.now()
	// 仅 iOS 且尚无键盘高度时,用 focus 高度做首帧占位;精确值仍以 keyboardheightchange 为准。
	// 安卓 focus 首次携带的高度经常是 0/旧值/错值,一律等键盘高度事件。
	const focusHeight = Number(e && e.detail && e.detail.height)
	if (isIOS && keyboardHeightPx.value === 0 && Number.isFinite(focusHeight) && focusHeight > 0) {
		scheduleKeyboardRaise(focusHeight, KB_DEFAULT_ANIM_MS)
	}
}

function onInputBlur() {
	inputFocused.value = false
	clearKbCollapseTimer()
	clearKeyboardCloseFallback()
	keyboardCloseFallbackTimer = setTimeout(() => {
		keyboardCloseFallbackTimer = null
		if (!inputFocused.value) collapseDock(isIOS ? KB_DEFAULT_ANIM_MS : 0)
	}, KEYBOARD_SCROLL_SETTLE_MS)
}

// 键盘高度事件是唯一权威来源:正值走合帧抬起;0 在聚焦初期按 iOS 过渡噪声忽略,其余延迟确认后收起。
function onKeyboardHeightChange(e) {
	const height = Number(e && e.detail && e.detail.height)
	if (!Number.isFinite(height) || height < 0) return
	const durationMs = normalizeKbDurationMs(e && e.detail && e.detail.duration)
	if (height > 0) {
		clearKeyboardCloseFallback()
		clearKbCollapseTimer()
		// 切换输入法或表情面板可能只回吐高度、不补发 focus
		inputFocused.value = true
		scheduleKeyboardRaise(height, durationMs)
		return
	}
	if (!inputFocused.value) {
		collapseDock(durationMs)
		return
	}
	// 聚焦初期的 0 是 iOS 弹起过渡期噪声,不是收起;安卓无此噪声且自带收起键只发 0,不得吞掉
	if (isIOS && Date.now() - focusAt < KB_ZERO_GRACE_MS) return
	clearKbCollapseTimer()
	kbCollapseTimer = setTimeout(() => {
		kbCollapseTimer = null
		// 安卓点键盘自带收起键只发 0、不触发 blur,确认收起后一并清理聚焦视觉状态
		inputFocused.value = false
		collapseDock(durationMs)
	}, KB_COLLAPSE_CONFIRM_MS)
}

function applyKeyboardHeight(height, durationMs) {
	const nextHeight = Math.max(0, Number(height) || 0)
	if (nextHeight === keyboardHeightPx.value) return
	const shouldCompensate = isNearBottom()
	// 起步位移取旧目标位置,须在写入 keyboardHeightPx 前捕获
	const previousShiftPx = Math.max(0, dockTargetBottomPx.value - idleDockBottomPx)
	const nextBottom =
		nextHeight > 0
			? Math.max(nextHeight + VISIBLE_KEYBOARD_GAP_PX, idleDockBottomPx)
			: idleDockBottomPx
	const nextShiftPx = Math.max(0, nextBottom - idleDockBottomPx)
	const previousInset = scrollInsetBottomPx.value
	clearScrollInsetTimer()
	const motionMs = Math.max(0, Number(durationMs) || 0)
	keyboardHeightPx.value = nextHeight
	animateDockTo(previousShiftPx, nextShiftPx, motionMs)
	if (nextBottom >= previousInset) {
		scrollInsetBottomPx.value = nextBottom
	} else {
		if (shouldCompensate && scrollTopPx > 0 && scrollHeightPx > 0) {
			const insetReduction = previousInset - nextBottom
			const nextMaxScrollTop = Math.max(
				0,
				scrollHeightPx - insetReduction - viewportPx
			)
			const nextScrollTop = Math.min(scrollTopPx, nextMaxScrollTop)
			if (nextScrollTop < scrollTopPx - 1) {
				commandScrollTop(nextScrollTop, true)
			}
		}
		scrollInsetTimer = setTimeout(() => {
			scrollInsetTimer = null
			scrollInsetBottomPx.value = nextBottom
		}, Math.max(KEYBOARD_SCROLL_SETTLE_MS, motionMs + DOCK_START_FRAME_MS))
	}
	// 贴底补偿等 Dock 过渡结束(含起步帧)再滚:键盘/Dock/消息三路并发各按各的曲线动,观感混乱
	scheduleObstructionCompensation(
		shouldCompensate,
		motionMs > 0 ? motionMs + DOCK_START_FRAME_MS + 30 : 24
	)
}

// 点击页面收起键盘由系统默认行为承担；长按打开排序浮层时需主动收起。
// Dock 以高度归零事件为准再复位，避免先于原生键盘下落；250ms 仅作事件缺失兜底。
function hideKeyboardIfFocused() {
	if (!inputFocused.value) return
	uni.hideKeyboard()
	inputFocused.value = false
	clearKbCollapseTimer()
	clearKeyboardCloseFallback()
	keyboardCloseFallbackTimer = setTimeout(() => {
		keyboardCloseFallbackTimer = null
		if (!inputFocused.value) collapseDock(isIOS ? KB_DEFAULT_ANIM_MS : 0)
	}, KEYBOARD_SCROLL_SETTLE_MS)
}

// ====== 输入态 ======
// 输入区只按已上屏文本增长，最多 10 行；更长内容在原生 textarea 内部滚动。
const MAX_INPUT_LINES = 10
const INPUT_LINE_RPX = 42
const INPUT_VERTICAL_PADDING_RPX = 44
const INPUT_MEASURE_DELAY_MS = 32
const inputText = ref('')
const inputLineCount = ref(1)
const inputSurfaceRpx = computed(
	() => inputLineCount.value * INPUT_LINE_RPX + INPUT_VERTICAL_PADDING_RPX
)
const inputSurfaceHeight = computed(() => `${inputSurfaceRpx.value}rpx`)
// 末尾零宽字符保证空文本和以换行结尾的文本都能测到最后一行。
const inputMeasureText = computed(() => `${inputText.value || ''}\u200b`)
let inputMeasureVersion = 0
let inputMeasureTimer = null

function clearInputMeasureTimer() {
	if (inputMeasureTimer) {
		clearTimeout(inputMeasureTimer)
		inputMeasureTimer = null
	}
}

function resetInputHeight() {
	inputMeasureVersion += 1
	clearInputMeasureTimer()
	inputLineCount.value = 1
}

function scheduleInputMeasure() {
	const version = ++inputMeasureVersion
	clearInputMeasureTimer()
	nextTick(() => {
		if (version !== inputMeasureVersion) return
		// 小程序逻辑层 nextTick 不等于视图层已完成排版。延后一帧并合并连续输入，
		// 避免快速删除时读到上一帧的 5 行高度，导致空白高度无法回落。
		inputMeasureTimer = setTimeout(() => {
			inputMeasureTimer = null
			if (version !== inputMeasureVersion) return
			const query = uni.createSelectorQuery()
			if (componentInstance && componentInstance.proxy) query.in(componentInstance.proxy)
			query
				.select('.input-measure')
				.boundingClientRect((rect) => {
					if (version !== inputMeasureVersion || !rect || !(rect.height > 0)) return
					const lineHeightPx = Math.max(1, uni.upx2px(INPUT_LINE_RPX))
					const measuredLines = Math.max(1, Math.round(rect.height / lineHeightPx))
					inputLineCount.value = Math.min(measuredLines, MAX_INPUT_LINES)
				})
				.exec()
		}, INPUT_MEASURE_DELAY_MS)
	})
}

function setInputText(value) {
	inputText.value = value == null ? '' : String(value)
	if (!inputText.value) {
		resetInputHeight()
		return
	}
	scheduleInputMeasure()
}

function onInputChange(e) {
	setInputText((e && e.detail && e.detail.value) || '')
}

// dock 高度取真实布局测量值，避免澄清条、Skill、图片和输入行数用硬编码相加产生漂移。
const dockHeightPx = ref(uni.upx2px(INPUT_LINE_RPX + INPUT_VERTICAL_PADDING_RPX))
let dockMeasureVersion = 0
function scheduleDockMeasure() {
	const version = ++dockMeasureVersion
	nextTick(() => {
		if (version !== dockMeasureVersion) return
		const query = uni.createSelectorQuery()
		if (componentInstance && componentInstance.proxy) query.in(componentInstance.proxy)
		query
			.select('.dock')
			.boundingClientRect((rect) => {
				if (version !== dockMeasureVersion || !rect || !(rect.height > 0)) return
				const shouldCompensate = rect.height > dockHeightPx.value && isNearBottom()
				dockHeightPx.value = rect.height
				if (shouldCompensate) scheduleObstructionCompensation(true)
			})
			.exec()
	})
}

// 留白跟随 Dock 目标位置，已有消息只在接近底部时做聊天式滚动避让。
const scrollPadBottom = computed(
	() => `${dockHeightPx.value + scrollInsetBottomPx.value + LAST_MESSAGE_GAP_PX}px`
)
// 滚动位置采样:仅用于判断用户是否仍贴着底部,避免自动滚动打断上翻查看历史
let scrollTopPx = 0
let scrollHeightPx = 0
function onPageScroll(e) {
	const d = e && e.detail
	if (!d) return
	const previousScrollTop = scrollTopPx
	scrollTopPx = d.scrollTop || 0
	scrollHeightPx = d.scrollHeight || 0
	if (
		scrollTopPx <= 60 &&
		scrollTopPx < previousScrollTop &&
		historyHasMore.value &&
		!historyOlderLoading.value
	) {
		loadOlderMessages()
	}
}
function isNearBottom() {
	if (!scrollHeightPx || !viewportPx) return true
	return scrollHeightPx - scrollTopPx - viewportPx < 150
}
// 待发送图片：上传图 {type:'upload',url} 或生成图 {type:'generated',taskId,seq,url}
const pendingImages = ref([])
const referenceUploading = ref(false)
let referenceUploadVersion = 0
// 后端精确绑定的待回答澄清；scopeId 只由客户端原样回传，不展示给用户。
const pendingClarification = computed({
	get: () => conversationStore.currentSession.pendingClarification,
	set: (value) => { conversationStore.currentSession.pendingClarification = value },
})
const selectedClarificationOptionId = ref('')
const orderEditorVisible = ref(false)
const pendingImageUrls = computed(() => pendingImages.value.map((img) => fullUrl(img)))
// 已激活的 Skill 名
const activeSkill = ref('')
// 是否有图仍在生成:任一消息的产出图状态未落定(非 SUCCEEDED/FAILED)。
// SSE 结束后 streaming 已 false 但出图仍在后台轮询,此时禁止发下一条,避免打断出图
const hasGeneratingImage = computed(() =>
	messages.value.some(
		(m) =>
			m.result &&
			Array.isArray(m.result.images) &&
			m.result.images.some((img) => img.status !== 'SUCCEEDED' && img.status !== 'FAILED')
	)
)
// 可发送:有非空输入 且 不在流式、无图生成中
const canSend = computed(
	() => inputText.value.trim().length > 0 && !interactionLocked.value && !hasGeneratingImage.value && !referenceUploading.value
)
const canEditMessage = computed(() => !interactionLocked.value && !hasGeneratingImage.value && !referenceUploading.value)
const favoriteSaving = ref(false)

// ====== 滚动 ======
const scrollIntoView = ref('')
const controlledScrollTop = ref(0)
const scrollWithAnimation = ref(true)
let scrollReleaseTimer = null
let scrollCommandVersion = 0
let scrollTopCommandVersion = 0
let obstructionMeasureVersion = 0

function messageDomId(message, index) {
	const raw = message && (message.id || message.renderKey)
	const normalized = String(raw == null ? index : raw).replace(/[^a-zA-Z0-9_-]/g, '-')
	return `chat-message-${normalized}`
}

function commandScrollTop(target, animate = true) {
	const version = ++scrollTopCommandVersion
	scrollCommandVersion += 1
	if (scrollReleaseTimer) {
		clearTimeout(scrollReleaseTimer)
		scrollReleaseTimer = null
	}
	scrollIntoView.value = ''
	scrollWithAnimation.value = animate
	nextTick(() => {
		if (version !== scrollTopCommandVersion) return
		const normalized = Math.max(0, Number(target) || 0)
		controlledScrollTop.value =
			Math.abs(controlledScrollTop.value - normalized) < 0.5 ? normalized + 0.5 : normalized
		setTimeout(() => {
			if (version === scrollTopCommandVersion) scrollWithAnimation.value = true
		}, animate ? 320 : 30)
	})
}

function scheduleObstructionCompensation(shouldCompensate, delayMs = 24) {
	const version = ++obstructionMeasureVersion
	if (!shouldCompensate || !messages.value.length) return
	nextTick(() => {
		setTimeout(() => {
			if (version !== obstructionMeasureVersion) return
			const lastIndex = messages.value.length - 1
			const selector = `#${messageDomId(messages.value[lastIndex], lastIndex)}`
			const query = uni.createSelectorQuery()
			if (componentInstance && componentInstance.proxy) query.in(componentInstance.proxy)
			query
				.select(selector)
				.boundingClientRect((rect) => {
					if (version !== obstructionMeasureVersion || !rect) return
					const dockTop = Math.max(
						viewportPx - dockTargetBottomPx.value - dockHeightPx.value,
						DOCK_TOP_MIN_PX
					)
					const overlap = rect.bottom + LAST_MESSAGE_GAP_PX - dockTop
					if (overlap > 1) commandScrollTop(scrollTopPx + overlap, true)
				})
				.exec()
		}, delayMs)
	})
}

// 使用一次性锚点滚到底部，动画结束后释放控制，避免恢复轮询锁住用户手势。
function scrollToBottom(animate = true) {
	const version = ++scrollCommandVersion
	scrollTopCommandVersion += 1
	scrollWithAnimation.value = animate
	nextTick(() => {
		if (version !== scrollCommandVersion) return
		scrollIntoView.value = ''
		nextTick(() => {
			if (version !== scrollCommandVersion) return
			scrollIntoView.value = 'chat-scroll-bottom'
			if (scrollReleaseTimer) clearTimeout(scrollReleaseTimer)
			scrollReleaseTimer = setTimeout(() => {
				if (version === scrollCommandVersion) scrollIntoView.value = ''
				scrollWithAnimation.value = true
				scrollReleaseTimer = null
			}, animate ? 350 : 50)
		})
	})
}

function followLatestIfNearBottom(animate = true) {
	if (isNearBottom()) scrollToBottom(animate)
}

// ====== 单 Skill ======
// 唯一可用 skill(getSkills 取第一条,可能为 null)
const skill = ref(null)
// skill 是否已激活:activeSkill 非空且等于该 skill 名
const skillActive = computed(() => !!skill.value && activeSkill.value === skill.value.name)

// 只要编辑器可见内容变化就重新测真实高度；真正发生遮挡时由几何测量做最小滚动补偿。
watch(
	[
		inputLineCount,
		() => pendingClarification.value,
		() => pendingImages.value.length,
		skillActive,
		() => chatModels.value.length,
		() => imageModels.value.length,
		() => conversationStore.currentSession.modelId,
		() => conversationStore.currentSession.imageModelId,
	],
	scheduleDockMeasure,
	{ immediate: true }
)

// ====== + 号弹窗 ======
const plusVisible = ref(false)

// ====== 历史浮层 ======
const historyVisible = ref(false)
const conversationRecords = ref([])
let conversationListVersion = 0
const conversations = computed(() => conversationRecords.value.map((item) => ({ ...item,
	runStatus: conversationStore.sessions[String(item.conversationId)]?.status || item.runStatus,
})))
const historyLoading = ref(false)
const historySwitchingId = ref(null)
const newConversationSwitching = ref(false)
const historyClosing = ref(false)
const historyCursor = ref(null)
const historyHasMore = ref(false)
const historyOlderLoading = ref(false)
let historySwitchVersion = 0
let historyMarkdownExpected = 0
let historyMarkdownLoaded = 0
let historyMarkdownResolve = null
let historyMarkdownTimer = null

// Each callback captures its session and request. Navigation never owns a run.
const streams = new Map()
const syncTimers = new Map()
const syncVersions = new Map()
let disposed = false
let sessionEpoch = 0
// 出图轮询定时器集合(用于卸载时清理)
const pollTimers = new Set()
const pollingTasks = new Map()
// 断线恢复轮询：服务端完成后会落 assistant 消息，客户端据此恢复最终对话。
const CONVERSATION_SYNC_INTERVAL = 3000

// ====== 工具方法 ======

// 拼接图片绝对地址:img 可能是字符串 url 或 {url}
function fullUrl(img) {
	const url = typeof img === 'string' ? img : img && img.url
	if (!url) return ''
	if (/^https?:\/\//.test(url)) return url
	return config.baseUrl + url
}

function createRequestId() {
	return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

// 该条是否正在流式输出(最后一条 + 流式态):流式中显纯文本,避免逐帧全量解析 markdown
function isStreamingMsg(mIdx) {
	return streaming.value && mIdx === messages.value.length - 1
}

// 是否处于「等待首帧」态(AI 占位无任何内容且在流式中)
function isWaiting(msg, mIdx) {
	return (
		streaming.value &&
		mIdx === messages.value.length - 1 &&
		!msg.thinking &&
		!msg.queueMessage &&
		!msg.text &&
		(!msg.tools || !msg.tools.length) &&
		!msg.clarification &&
		!msg.result
	)
}

// 预览用户图片
function previewUserImg(imgs, idx) {
	const urls = imgs.map((i) => fullUrl(i))
	uni.previewImage({ urls, current: urls[idx] })
}

// 预览待发送图片，顺序与发送给后端的图片编号一致
function previewPendingImg(idx) {
	const urls = pendingImages.value.map((i) => fullUrl(i))
	uni.previewImage({ urls, current: urls[idx] })
}

// ====== 顶栏 / 历史 ======

function goRecharge() {
	uni.navigateTo({ url: '/pages/bean/recharge/index?resumeTask=1' })
}

function openHistory() {
	hideKeyboardIfFocused()
	historyClosing.value = false
	historyVisible.value = true
	loadConversations()
}

defineExpose({ openHistory, goRecharge })

function closeHistory() {
	if (historySwitchingId.value != null || newConversationSwitching.value) return
	historyClosing.value = true
	setTimeout(() => {
		historyVisible.value = false
		historyClosing.value = false
	}, 120)
}

async function loadConversations() {
	const epoch = sessionEpoch, version = ++conversationListVersion
	const isLatest = () => !disposed && epoch === sessionEpoch && version === conversationListVersion
	historyLoading.value = true
	try {
		const res = await listConversations(1, 30)
		if (!isLatest()) return
		conversationRecords.value = (res && res.records) || []
		conversationRecords.value.forEach((item) => {
			const session = conversationStore.sessions[String(item.conversationId)]
			if (session && !streams.has(String(item.conversationId))
				&& (!session.requestId || sameId(session.requestId, item.runId))) session.status = item.runStatus || ''
		})
	} catch (e) {
		if (isLatest()) conversationRecords.value = []
	} finally {
		if (isLatest()) historyLoading.value = false
	}
}

function clearHistoryMarkdownWait() {
	if (historyMarkdownTimer) clearTimeout(historyMarkdownTimer)
	historyMarkdownTimer = null
	if (historyMarkdownResolve) historyMarkdownResolve()
	historyMarkdownResolve = null
	historyMarkdownExpected = 0
	historyMarkdownLoaded = 0
}

function waitForHistoryMarkdown(loaded) {
	clearHistoryMarkdownWait()
	historyMarkdownExpected = loaded.filter(
		(message) => message && message.role === 'assistant' && message.text && !message.clarification
	).length
	if (!historyMarkdownExpected) return Promise.resolve()
	return new Promise((resolve) => {
		historyMarkdownResolve = resolve
		historyMarkdownTimer = setTimeout(clearHistoryMarkdownWait, 360)
	})
}

function onHistoryMarkdownLoad() {
	if (!historyMarkdownResolve) return
	historyMarkdownLoaded += 1
	if (historyMarkdownLoaded >= historyMarkdownExpected) clearHistoryMarkdownWait()
}

function delay(ms) {
	return new Promise((resolve) => setTimeout(resolve, ms))
}

async function revealPreparedConversation(version) {
	if (version !== historySwitchVersion) return
	scrollToBottom(false)
	await delay(50)
	if (version !== historySwitchVersion) return
	historyClosing.value = true
	await delay(120)
	if (version !== historySwitchVersion) return
	historyVisible.value = false
	historyClosing.value = false
}

// Preserve each conversation's unsent inputs as well as its live output.
function saveInputDraft() {
	conversationStore.currentSession.draft = { text: inputText.value, images: pendingImages.value.slice(),
		skill: activeSkill.value, option: selectedClarificationOptionId.value }
	cancelReferenceUpload()
}
function restoreInputDraft() {
	const draft = conversationStore.currentSession.draft || {}
	setInputText(draft.text || '')
	pendingImages.value = draft.images || []
	activeSkill.value = draft.skill || ''
	selectedClarificationOptionId.value = draft.option || ''
	orderEditorVisible.value = false
	ensureModelSelection()
}
async function onHistoryNew() {
	if (preparingConversation.value) return
	const version = ++historySwitchVersion
	newConversationSwitching.value = true
	saveInputDraft()
	conversationStore.reset()
	historyCursor.value = null
	historyHasMore.value = false
	historyOlderLoading.value = false
	restoreInputDraft()
	scrollCommandVersion += 1
	scrollIntoView.value = ''
	commandScrollTop(0, false)
	await nextTick()
	await revealPreparedConversation(version)
	if (version === historySwitchVersion) newConversationSwitching.value = false
}
async function onHistoryPick(item) {
	if (preparingConversation.value) return
	const version = ++historySwitchVersion
	historySwitchingId.value = item.conversationId
	saveInputDraft()
	const loaded = await loadConversation(item.conversationId, true, true, version)
	if (loaded && version === historySwitchVersion) await revealPreparedConversation(version)
	if (version === historySwitchVersion) historySwitchingId.value = null
}

function sameId(left, right) {
	return left != null && right != null && String(left) === String(right)
}

function clearConversationSync(conversationId) {
	if (conversationId == null) {
		for (const key of syncTimers.keys()) clearConversationSync(key)
		return
	}
	const key = String(conversationId)
	syncVersions.set(key, (syncVersions.get(key) || 0) + 1)
	clearTimeout(syncTimers.get(key))
	syncTimers.delete(key)
}
function isCurrentSession(id) { return sameId(conversationStore.conversationId, id) }
const ACTIVE_RUN_STATES = new Set(['QUEUED', 'RUNNING', 'GENERATING'])
function applyConversationSnapshot(conversationId, list, pending, pageInfo = null) {
	const session = conversationStore.getSession(conversationId)
	session.messages = normalizeHistoryMessages(list)
	session.pendingClarification = pending && pending.scopeId ? pending : null
	if (pageInfo) { session.nextCursor = pageInfo.nextCursor; session.hasMore = !!pageInfo.hasMore }
	session.messages.forEach((message) => startResultPolling(message.result))
	return session.messages
}

function appendRecoveryPlaceholder(sourceMessageId) {
	const last = conversationStore.messages[conversationStore.messages.length - 1]
	if (last && last.role === 'assistant' && last.recoveryPlaceholder) return last
	const placeholder = {
		role: 'assistant',
		queueMessage: '任务正在后台处理中',
		thinking: '',
		text: '',
		tools: [],
		result: null,
		sourceMessageId,
		recoveryPlaceholder: true,
		renderKey: `recovery-${sourceMessageId || conversationStore.conversationId}`,
	}
	conversationStore.addMessage(placeholder)
	return conversationStore.messages[conversationStore.messages.length - 1]
}

function hasGenerationTasks(ai) {
	return !!(
		ai &&
		ai.result &&
		Array.isArray(ai.result.images) &&
		ai.result.images.some((img) => img && img.taskId)
	)
}

function applyGenerationTaskSnapshot(result, task) {
	if (!result || !task || !task.taskId) return
	const status = task.status || 'RUNNING'
	const urls = Array.isArray(task.resultUrls) ? task.resultUrls : safeParse(task.resultUrls) || []
	const previews = Array.isArray(task.previewUrls) ? task.previewUrls : safeParse(task.previewUrls) || []
	if (status === 'SUCCEEDED' || status === 'RUNNING') {
		markTaskImages(result, task.taskId, status, urls, previews)
	} else if (status === 'FAILED' || status === 'CANCELLED') {
		markTaskImages(result, task.taskId, 'FAILED', urls, previews)
	} else {
		markTaskImages(result, task.taskId, status, null)
	}
}

function applyRunSnapshot(session, run) {
	if (!run) return
	const running = ACTIVE_RUN_STATES.has(run.status)
	const rechargeRequired = run.status === 'WAITING_RECHARGE'
		|| (run.status === 'WAITING_CONFIRMATION' && run.clarification?.key === 'INSUFFICIENT_BALANCE')
	session.status = run.status
	session.requestId = run.requestId || run.runId
	session.activeMessageId = running ? (run.messageId || run.requestId || run.runId) : null
	session.recovering = running
	session.streaming = false
	if (run.modelId != null && session.modelId == null) session.modelId = run.modelId
	if (run.imageModelId != null && session.imageModelId == null) session.imageModelId = run.imageModelId
	if (run.clarification) session.pendingClarification = run.clarification
	if (rechargeRequired) {
		if (run.messageId != null) {
			const previous = sameId(session.pendingResumeTask?.sourceMessageId, run.messageId) ? session.pendingResumeTask : null
			conversationStore.setPendingResumeTask({
				...previous, conversationId: session.conversationId, sourceMessageId: run.messageId,
				modelId: run.modelId, imageModelId: run.imageModelId,
				continuationMode: run.clarification?.scopeId || run.assistantMessageId != null ? 'confirmation' : 'resume',
				clarificationScopeId: run.clarification?.scopeId || previous?.clarificationScopeId,
				clarificationOptionId: run.clarification?.key === 'INSUFFICIENT_BALANCE' ? 'generate_requested' : previous?.clarificationOptionId,
				ready: previous?.ready === true,
			}, session.conversationId)
		} else conversationStore.clearPendingResumeTask(session.conversationId)
	} else if ((running || ['COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED'].includes(run.status))
		&& sameId(session.pendingResumeTask?.sourceMessageId, run.messageId)) {
		// Another device or a previous reconnect may have already resumed this message.
		conversationStore.clearPendingResumeTask(session.conversationId)
	}
	let ai = session.messages.find((message) => message.renderKey === `run-${run.runId}` || message.renderKey === `local-assistant-${run.requestId}` || (message.role === 'assistant' && (sameId(message.sourceMessageId, run.messageId) || sameId(message.id, run.assistantMessageId))))
	if (!ai && (running || rechargeRequired || run.text || run.thinking || run.result || run.errorMessage)) {
		ai = { renderKey: `run-${run.runId}`, role: 'assistant', text: '', thinking: '', tools: [], result: null }
		session.messages.push(ai)
		ai = session.messages[session.messages.length - 1]
	}
	if (!ai) return
	ai.sourceMessageId = run.messageId
	ai.text = run.text || (rechargeRequired ? run.errorMessage || '豆子余额不足，请充值后继续' :
		run.status === 'FAILED' || run.status === 'INTERRUPTED' ? run.errorMessage || '任务未完成，请重试' : '')
	ai.rechargeRequired = rechargeRequired
	ai.rechargeRequestId = run.requestId || run.runId
	ai.thinking = run.thinking || ''
	ai.queueMessage = running ? (run.status === 'QUEUED' ? '排队中，请稍候' : '任务正在后台处理') : ''
	ai.clarification = run.clarification || null
	ai.stopped = run.status === 'CANCELLED'
	if (run.result) ai.result = mergeResultData(ai.result, run.result)
	for (const task of run.generationTasks || []) {
		if (!ai.result) ai.result = { images: [] }
		if (!Array.isArray(ai.result.images)) ai.result.images = []
		for (let seq = 1; seq <= Math.max(1, Number(task.n) || 1); seq++) {
			if (!ai.result.images.some((img) => img.taskId === task.taskId && img.seq === seq)) ai.result.images.push({ taskId: task.taskId, seq })
		}
		applyGenerationTaskSnapshot(ai.result, task)
	}
	startResultPolling(ai.result)
}
function startConversationSync(conversationId) {
	if (conversationId == null || disposed) return
	clearConversationSync(conversationId)
	const key = String(conversationId), version = syncVersions.get(key)
	const expectedRequestId = conversationStore.getSession(conversationId).requestId
	let missingRunCount = 0
	const tick = async () => {
		if (disposed || syncVersions.get(key) !== version || streams.has(key)) return
		try {
			const run = await getConversationRun(conversationId, { showError: false })
			if (disposed || syncVersions.get(key) !== version || streams.has(key)) return
			const session = conversationStore.getSession(conversationId)
			if (expectedRequestId && (!run || String(run.requestId || run.runId) !== String(expectedRequestId))) {
				missingRunCount += 1
				if (missingRunCount < 3) { syncTimers.set(key, setTimeout(tick, CONVERSATION_SYNC_INTERVAL)); return }
				session.recovering = false
				session.activeMessageId = null
				session.status = 'FAILED'
				const last = session.messages[session.messages.length - 1]
				if (last?.role === 'assistant') { last.queueMessage = ''; if (!last.text) last.text = session.transportError || '未查询到本次任务，请重新发送' }
				conversationStore.persist()
				clearConversationSync(conversationId)
				return
			}
			applyRunSnapshot(session, run)
			conversationStore.persist()
			if (!run || !ACTIVE_RUN_STATES.has(run.status)) {
				const page = await getMessagePage(conversationId, null, 30, { showError: false })
				if (disposed || syncVersions.get(key) !== version || streams.has(key)) return
				applyConversationSnapshot(conversationId, page?.records || [], run?.clarification, page)
				// Preserve interrupted/failed run diagnostics if no assistant record exists.
				if (run) applyRunSnapshot(session, run)
				session.recovering = false
				session.activeMessageId = null
				conversationStore.persist()
				clearConversationSync(conversationId)
				refreshBalance()
				if (historyVisible.value) loadConversations()
				return
			}
		} catch { /* A transient network error must not discard a running task. */ }
		if (!disposed && syncVersions.get(key) === version) syncTimers.set(key, setTimeout(tick, CONVERSATION_SYNC_INTERVAL))
	}
	syncTimers.set(key, setTimeout(tick, CONVERSATION_SYNC_INTERVAL))
}
async function loadConversation(conversationId, showError = false, settleForReveal = false, switchVersion = historySwitchVersion) {
	const session = conversationStore.getSession(conversationId)
	const initialRequestId = session.requestId
	const epoch = sessionEpoch
	const loadVersion = (session.historyLoadVersion || 0) + 1
	session.historyLoadVersion = loadVersion
	session.loadingHistory = !streams.has(String(conversationId))
	const ownsSnapshot = () => !disposed && epoch === sessionEpoch && switchVersion === historySwitchVersion
		&& conversationStore.getSession(conversationId) === session
		&& session.historyLoadVersion === loadVersion
		&& session.requestId === initialRequestId && !streams.has(String(conversationId))
	try {
		if (!streams.has(String(conversationId))) {
			const [page, pending, run] = await Promise.all([
				getMessagePage(conversationId, null, 30, { showError: false }),
				getPendingClarification(conversationId, { showError: false }).catch(() => null),
				getConversationRun(conversationId, { showError: false }),
			])
			// A send can start while the history/run requests are awaiting the network.
			// Never replace the reactive message objects owned by that newer stream.
			if (!ownsSnapshot()) return false
			applyConversationSnapshot(conversationId, page?.records || [], pending, page)
			if (run) applyRunSnapshot(session, run)
			else { session.recovering = false; session.activeMessageId = null; session.status = '' }
			if (run && ACTIVE_RUN_STATES.has(run.status)) startConversationSync(conversationId)
		}
		if (switchVersion !== historySwitchVersion) return false
		conversationStore.setConversationId(conversationId)
		historyCursor.value = session.nextCursor
		historyHasMore.value = session.hasMore
		restoreInputDraft()
		if (settleForReveal) await waitForHistoryMarkdown(session.messages)
		scrollToBottom(false)
		return true
	} catch (error) {
		if (showError && !disposed && epoch === sessionEpoch && switchVersion === historySwitchVersion) uni.showToast({ title: '加载会话失败', icon: 'none' })
		return false
	} finally {
		if (session.historyLoadVersion === loadVersion) session.loadingHistory = false
	}
}

function queryRect(selector) {
	return new Promise((resolve) => {
		const query = uni.createSelectorQuery()
		if (componentInstance && componentInstance.proxy) query.in(componentInstance.proxy)
		query
			.select(selector)
			.boundingClientRect((rect) => resolve(rect || null))
			.exec()
	})
}

async function loadOlderMessages() {
	const conversationId = conversationStore.conversationId
	const beforeId = historyCursor.value
	if (
		conversationId == null ||
		beforeId == null ||
		!historyHasMore.value ||
		historyOlderLoading.value ||
		historySwitchingId.value != null ||
		newConversationSwitching.value
	) {
		return
	}
	const first = messages.value[0]
	const firstSelector = first ? `#${messageDomId(first, 0)}` : ''
	const beforeRect = firstSelector ? await queryRect(firstSelector) : null
	const previousScrollTop = scrollTopPx
	historyOlderLoading.value = true
	try {
		const page = await getMessagePage(conversationId, beforeId, 30, { showError: false })
		if (!sameId(conversationStore.conversationId, conversationId)) return
		const older = normalizeHistoryMessages((page && page.records) || [])
		const markdownReady = waitForHistoryMarkdown(older)
		conversationStore.prependMessages(older)
		historyCursor.value = page && page.nextCursor != null ? page.nextCursor : null
		historyHasMore.value = !!(page && page.hasMore)
		conversationStore.messages
			.slice(0, older.length)
			.forEach((message) => startResultPolling(message.result))
		await nextTick()
		await markdownReady
		await delay(30)
		if (beforeRect && firstSelector) {
			const afterRect = await queryRect(firstSelector)
			if (afterRect) {
				commandScrollTop(previousScrollTop + (afterRect.top - beforeRect.top), false)
			}
		}
		measureUserMessages()
	} catch (e) {
		uni.showToast({ title: '更早消息加载失败', icon: 'none' })
	} finally {
		historyOlderLoading.value = false
	}
}

// 历史消息(MessageVO 数组)转为前端渲染结构
function normalizeHistoryMessages(list) {
	if (!Array.isArray(list)) return []
	const result = []
	for (const m of list) {
		if (m.role === 'user') {
			result.push({
				id: m.id,
				renderKey: `message-${m.id}`,
				role: 'user',
				content: m.content || '',
				images: parseMediaImages(m.media),
				collapsible: false,
				expanded: false,
			})
		} else if (m.role === 'assistant') {
			// 结构化结果与澄清卡片分别恢复，其他类型按纯文本展示。
			const ai = {
				id: m.id,
				renderKey: `message-${m.id}`,
				role: 'assistant',
				queueMessage: '',
				thinking: '',
				text: '',
				tools: [],
				result: null,
			}
			if (m.contentType === 'structured') {
				const parsed = safeParse(m.content) || null
				if (parsed && parsed.body === '对话已停止，已提交图片继续生成') {
					// 兼容修复前的停止记录：图片只进入“我的作品”，不再恢复到对话。
					ai.stopped = true
				} else {
					ai.result = parsed
					// content 里 images 仅含 {taskId,seq};后端已把 media 解析为 [{taskId,seq,url}],据此回填图 url
					overlayMediaUrls(ai.result, safeParse(m.media))
					normalizeResultImages(ai.result)
				}
			} else if (m.contentType === 'clarification') {
				ai.clarification = safeParse(m.media) || { question: m.content || '' }
				ai.text = m.content || ''
			} else if (m.content === '对话已停止') {
				ai.stopped = true
			} else {
				ai.text = m.content || ''
			}
			result.push(ai)
		}
		// role=tool 的历史消息不单独渲染(已并入对话流语义),跳过
	}
	return result
}

// 解析用户消息图片；历史旧数据只有 URL 时按上传图恢复，顺序保持不变。
function parseMediaImages(media) {
	const arr = safeParse(media)
	if (!Array.isArray(arr)) return []
	return arr
		.map((it) => {
			if (typeof it === 'string') return { type: 'upload', url: it }
			if (!it || !it.url) return null
			return it.taskId && it.seq
				? { type: 'generated', taskId: it.taskId, seq: it.seq, url: it.url }
				: { type: 'upload', url: it.url }
		})
		.filter(Boolean)
}

// 用后端解析过的 media([{taskId,seq,url}])回填结构化结果图的 url(按 taskId+seq 匹配)
function overlayMediaUrls(result, mediaArr) {
	if (!result || !Array.isArray(result.images) || !Array.isArray(mediaArr)) return
	result.images.forEach((img) => {
		const m = mediaArr.find((x) => x && x.taskId === img.taskId && x.seq === img.seq)
			if (m && m.url) {
				img.url = m.url
				img.previewUrl = m.previewUrl || m.url
			}
	})
}

// 历史结果图:补全展示字段(若已带 url 视为成功)
function normalizeResultImages(result) {
	if (!result || !Array.isArray(result.images)) return
	result.images = result.images.map((img) => {
		const url = img.url || img.fullUrl
		return {
			taskId: img.taskId,
			seq: img.seq,
			status: url ? 'SUCCEEDED' : img.status || 'RUNNING',
			url: url || '',
			fullUrl: url ? fullUrl(url) : '',
			previewUrl: url ? fullUrl(img.previewUrl || url) : '',
		}
	})
}

function safeParse(str) {
	if (!str || typeof str !== 'string') return null
	try {
		return JSON.parse(str)
	} catch (e) {
		return null
	}
}

// ====== 单 Skill ======

// 拉取可用 skill,取第一条作为唯一 skill
async function loadSkill() {
	// 未登录不请求:接口需登录态,游客业务码 401 会被 forceLogout 踢到登录页(与余额同守卫)
	if (!userStore.isLoggedIn) return
	try {
		const list = await getSkills()
		const first = Array.isArray(list) && list.length ? list[0] : null
		skill.value = first || null
	} catch (e) {
		skill.value = null
	}
}

// 切换 skill 激活/取消:激活时把 skill.name 存入 activeSkill,取消则清空
function toggleSkill() {
	if (!skill.value) return
	activeSkill.value = skillActive.value ? '' : skill.value.name
}

// ====== + 号:自定义弹窗 ======

function openPlus() {
	if (interactionLocked.value) return
	// 再点一次 + 号关闭
	const nextVisible = !plusVisible.value
	if (nextVisible) hideKeyboardIfFocused()
	plusVisible.value = nextVisible
	// 打开时若 skill 尚未拉到(如挂载时未登录、登录后页面不重挂),补拉一次
	if (plusVisible.value && !skill.value) loadSkill()
}

// 上传按钮与输入框已分区，关闭弹层不会再冒泡触发输入框聚焦。
function onPlusClose() {
	plusVisible.value = false
}

// 弹窗内「上传图片」:关闭弹窗后选图上传
function onPlusUpload() {
	onPlusClose()
	chooseAndUpload()
}

// 弹窗内「激活 Skill」:切换激活态后关闭弹窗,反馈由顶栏激活芯片承接
function onPlusToggleSkill() {
	toggleSkill()
	onPlusClose()
}

// 选图并上传,成功后收进 pendingImages
function chooseAndUpload() {
	if (interactionLocked.value || isReferenceUploadPending()) return
	const remaining = 9 - pendingImages.value.length
	if (remaining <= 0) {
		uni.showToast({ title: '最多上传9张图片', icon: 'none' })
		return
	}
	const uploadVersion = ++referenceUploadVersion
	referenceUploading.value = true
	uni.chooseImage({
		count: remaining,
		sizeType: ['compressed'],
		success: async (res) => {
			const paths = res.tempFilePaths || []
			const uploaded = []
			try {
				for (const p of paths) {
					if (uploadVersion !== referenceUploadVersion) return
					try {
						const data = await uploadFile(p, 'agent')
						if (uploadVersion !== referenceUploadVersion) return
						if (data && data.url) {
							// 按本次选图顺序收集，不让不同批次按上传完成时间交错。
							uploaded.push({ type: 'upload', url: data.url })
						}
					} catch (e) {
						// 单张失败已由 uploadFile 内部 toast,继续下一张
					}
				}
				if (uploadVersion === referenceUploadVersion && uploaded.length) {
					pendingImages.value = pendingImages.value.concat(uploaded).slice(0, 9)
				}
			} finally {
				if (uploadVersion === referenceUploadVersion) referenceUploading.value = false
			}
		},
		fail: () => {
			if (uploadVersion === referenceUploadVersion) referenceUploading.value = false
		},
	})
}

function isReferenceUploadPending() {
	if (!referenceUploading.value) return false
	uni.showToast({ title: '参考图上传中，请稍候', icon: 'none' })
	return true
}

function cancelReferenceUpload() {
	referenceUploadVersion += 1
	referenceUploading.value = false
}

// 将成功生成图加入下一轮输入框；只传任务号和序号给后端做归属校验，不信任图片 URL。
function addGeneratedReference(img) {
	if (isReferenceUploadPending()) return
	if (!img || !img.taskId || !img.seq || !img.url) return
	const duplicated = pendingImages.value.some(
		(item) => item.type === 'generated' && item.taskId === img.taskId && item.seq === img.seq
	)
	if (duplicated) {
		uni.showToast({ title: '该图片已添加', icon: 'none' })
		return
	}
	if (pendingImages.value.length >= 9) {
		uni.showToast({ title: '最多添加9张图片', icon: 'none' })
		return
	}
	pendingImages.value.push({
		type: 'generated',
		taskId: img.taskId,
		seq: img.seq,
		url: img.url,
	})
	uni.showToast({ title: '已添加为图片' + pendingImages.value.length, icon: 'none' })
}

function removeImage(idx) {
	pendingImages.value.splice(idx, 1)
}

function openImageOrderEditor() {
	if (isReferenceUploadPending()) return
	if (pendingImages.value.length < 2) return
	// 长按不走 tap 路径,系统未必自动收键盘;浮层是页内 fixed 遮罩,不收会盖住底部按钮
	hideKeyboardIfFocused()
	orderEditorVisible.value = true
}

function applyPendingImageOrder(order) {
	if (!Array.isArray(order) || order.length !== pendingImages.value.length) return
	const current = pendingImages.value.slice()
	const next = order.map((index) => current[index])
	if (next.some((item) => !item)) return
	pendingImages.value = next
	orderEditorVisible.value = false
}

function isActiveClarification(clarification) {
	return !!(
		clarification &&
		pendingClarification.value &&
		clarification.scopeId === pendingClarification.value.scopeId
	)
}

function findClarificationSourceMessageId(message) {
	if (message && message.sourceMessageId != null) return message.sourceMessageId
	const index = messages.value.indexOf(message)
	for (let i = index - 1; i >= 0; i -= 1) {
		const candidate = messages.value[i]
		if (candidate && candidate.role === 'user' && candidate.id != null) return candidate.id
	}
	return null
}

function selectClarificationOption(message, option) {
	const clarification = message && message.clarification
	if (!isActiveClarification(clarification) || !option || !option.id) return
	if (clarification.key === 'INSUFFICIENT_BALANCE' && option.id === 'recharge') {
		const sourceMessageId = findClarificationSourceMessageId(message)
		if (sourceMessageId != null) {
			conversationStore.setPendingResumeTask({
				conversationId: conversationStore.conversationId,
				sourceMessageId,
				skill: activeSkill.value || undefined,
				clarificationScopeId: clarification.scopeId,
				clarificationOptionId: 'generate_requested',
				ready: false,
			})
		}
		goRecharge()
		return
	}
	selectedClarificationOptionId.value = option.id
	setInputText(option.label || '按推荐方案处理')
	handleSend()
}

function startNewTaskFromClarification() {
	pendingClarification.value = null
	selectedClarificationOptionId.value = ''
	setInputText('')
	uni.showToast({ title: '已退出刚才的问题', icon: 'none' })
}

// ====== 发送 / 停止 ======

async function handleSend() {
	if (interactionLocked.value) return
	if (isReferenceUploadPending()) return
	// 出图未完成禁止发送(与 canSend 一致,防按钮外的回车等触发)
	if (hasGeneratingImage.value) {
		uni.showToast({ title: '图片生成中,请稍候', icon: 'none' })
		return
	}
	const text = inputText.value.trim()
	if (!text) return
	if (!requireLogin()) return
	// 用户主动发起新任务时，放弃此前未支付完成的自动恢复记录。
	if (conversationStore.pendingResumeTask && !conversationStore.pendingResumeTask.ready) {
		conversationStore.clearPendingResumeTask()
	}
	if (conversationStore.conversationId == null) {
		const epoch = sessionEpoch
		const draftSession = conversationStore.currentSession
		const submittedDraft = { text, images: pendingImages.value.slice(),
			skill: activeSkill.value, option: selectedClarificationOptionId.value }
		preparingConversation.value = true
		try {
			const created = await createConversation(text.slice(0, 50))
			if (disposed || epoch !== sessionEpoch) return
			if (!created || created.conversationId == null) throw new Error('创建会话失败')
			if (conversationStore.currentSession !== draftSession) {
				const createdSession = conversationStore.getSession(created.conversationId)
				createdSession.modelId = draftSession.modelId
				createdSession.imageModelId = draftSession.imageModelId
				createdSession.draft = submittedDraft
				conversationStore.persist()
				return
			}
			conversationStore.setConversationId(created.conversationId)
		} catch (e) {
			if (disposed || epoch !== sessionEpoch) return
			uni.showToast({ title: (e && e.message) || '创建会话失败', icon: 'none' })
			return
		} finally {
			if (epoch === sessionEpoch) preparingConversation.value = false
		}
	}

	// 联合图片入参保持当前数组顺序，第一项即图片1。
	const imageInputs = pendingImages.value.map((item) =>
		item.type === 'generated'
			? { type: 'generated', taskId: item.taskId, seq: item.seq }
			: { type: 'upload', url: item.url }
	)
	const clarificationScopeId = pendingClarification.value && pendingClarification.value.scopeId
	const clarificationOptionId = selectedClarificationOptionId.value || undefined
	const requestId = createRequestId()
	conversationStore.currentSession.requestId = requestId
	conversationStore.currentSession.status = 'QUEUED'
	conversationStore.setActiveMessageId(requestId)

	// 1) push 用户消息
	conversationStore.addMessage({
		renderKey: `local-user-${requestId}`,
		role: 'user',
		content: text,
		images: pendingImages.value.slice(),
		collapsible: false,
		expanded: false,
	})
	measureUserMessages()

	// 2) push 空 AI 占位消息
	const aiMsg = {
		renderKey: `local-assistant-${requestId}`,
		role: 'assistant',
		queueMessage: '',
		thinking: '',
		text: '',
		tools: [],
		result: null,
		sourceMessageId: null,
	}
	conversationStore.addMessage(aiMsg)
	// 取数组中的实际响应式对象(addMessage 内部 push,引用一致)
	const ai = conversationStore.messages[conversationStore.messages.length - 1]

	// 3) 进入流式
	conversationStore.setStreaming(true)

	// 清空输入态(skill 保留,余下随会话延续)
	const skill = activeSkill.value || undefined
	setInputText('')
	pendingImages.value = []
	pendingClarification.value = null
	selectedClarificationOptionId.value = ''

	scrollToBottom()

	// 4) 发起流式请求
	const streamPayload = {
			requestId,
			conversationId: conversationStore.conversationId,
			message: text,
			modelId: conversationStore.currentSession.modelId || undefined,
			imageModelId: conversationStore.currentSession.imageModelId || undefined,
			imageInputs: imageInputs.length ? imageInputs : undefined,
			skill,
			clarificationScopeId: clarificationScopeId || undefined,
			clarificationOptionId,
	}
	streams.set(String(streamPayload.conversationId), chatStream(streamPayload, createStreamHandlers(ai, streamPayload)))
}

function createStreamHandlers(ai, streamPayload) {
	const id = streamPayload.conversationId
	const session = conversationStore.getSession(id)
	const epoch = sessionEpoch
	const valid = () => !disposed && epoch === sessionEpoch && session.requestId === streamPayload.requestId
	const follow = () => { if (isCurrentSession(id)) followLatestIfNearBottom() }
	const guarded = (handler) => (event) => { if (valid()) handler(event) }
	return {
		onQueue: guarded((event) => { ai.queueMessage = event?.message || '排队中，请稍候'; session.status = 'QUEUED'; follow() }),
		onMeta: guarded((event) => {
			ai.queueMessage = ''; session.status = 'RUNNING'
			if (event?.messageId != null) { ai.sourceMessageId = event.messageId; conversationStore.setActiveMessageId(event.messageId, id) }
		}),
		onThinking: guarded((event) => { ai.queueMessage = ''; ai.thinking += event?.content || ''; follow() }),
		onText: guarded((event) => { ai.queueMessage = ''; ai.text += event?.content || ''; follow() }),
		onTool: guarded((event) => {
			mergeTool(ai, event)
			if (event?.name === 'generate_images' && event.status === 'done' && event.taskId) {
				ai.result = mergeResultData(ai.result, { images: Array.from({ length: Number(event.n) || 1 }, (_, index) => ({ taskId: event.taskId, seq: index + 1 })) })
				startResultPolling(ai.result)
			}
			follow()
		}),
		onClarification: guarded((event) => {
			if (!event?.clarification?.scopeId) return
			ai.clarification = event.clarification; session.pendingClarification = event.clarification
			if (isCurrentSession(id)) selectedClarificationOptionId.value = ''
			follow()
		}),
		onResult: guarded((event) => { ai.result = mergeResultData(ai.result, event?.data || {}); startResultPolling(ai.result); follow() }),
		onDone: guarded(() => {
			ai.queueMessage = ''
			if (sameId(session.pendingResumeTask?.sourceMessageId, ai.sourceMessageId)) conversationStore.clearPendingResumeTask(id)
			finishStream(id, streamPayload.requestId)
			// Server status includes image tasks still running after text completion.
			session.recovering = true
			startConversationSync(id)
			refreshBalance()
		}),
		onError: guarded((event) => {
			const recharge = event?.errorCode === 'INSUFFICIENT_BEANS' && event.action === 'RECHARGE'
			if (recharge && ai.sourceMessageId != null) conversationStore.setPendingResumeTask({
				conversationId: id, sourceMessageId: ai.sourceMessageId, modelId: streamPayload.modelId,
				imageModelId: streamPayload.imageModelId, skill: streamPayload.skill,
				clarificationScopeId: streamPayload.clarificationScopeId, clarificationOptionId: streamPayload.clarificationOptionId, ready: false,
			}, id)
			finishStream(id, streamPayload.requestId)
			// An HTTP/proxy error does not prove that the server rejected the run.
			session.transportError = event?.message || '连接异常，请重试'
			ai.queueMessage = '正在核对后台任务状态'
			session.recovering = true
			startConversationSync(id)
			if (recharge && isCurrentSession(id)) uni.showModal({ title: '豆子余额不足', content: event.message || '请充值后继续', confirmText: '去充值', success: (res) => { if (res.confirm) goRecharge() } })
		}),
	}
}

async function tryResumePendingTask() {
	let pending = conversationStore.pendingResumeTask
	if (!pending || !pending.ready || interactionLocked.value) return
	if (pending.conversationId == null || pending.sourceMessageId == null) {
		conversationStore.clearPendingResumeTask()
		return
	}
	const loaded = await loadConversation(pending.conversationId, true)
	const restoredPending = conversationStore.pendingResumeTask
	if (!loaded || interactionLocked.value || !restoredPending?.ready
		|| !sameId(restoredPending.sourceMessageId, pending.sourceMessageId)) return
	pending = restoredPending
	const requestId = createRequestId()
	const confirmation = pending.continuationMode === 'confirmation' || !!pending.clarificationScopeId
	const confirmationText = '充值已到账，请按原计划继续生成'
	let ai
	if (confirmation) {
		// Answering a saved clarification needs a new user message to consume its scope.
		// Keep the original prompt and budget card as history.
		conversationStore.addMessage({ renderKey: `local-user-${requestId}`, role: 'user', content: confirmationText, images: [] })
		conversationStore.addMessage({ renderKey: `local-assistant-${requestId}`, role: 'assistant', text: '', thinking: '', tools: [], result: null })
		ai = conversationStore.messages[conversationStore.messages.length - 1]
		conversationStore.currentSession.pendingClarification = null
	} else {
		ai = conversationStore.messages[conversationStore.messages.length - 1]
		if (!ai || ai.role !== 'assistant') ai = appendRecoveryPlaceholder(pending.sourceMessageId)
	}
	ai.queueMessage = '充值已到账，正在继续原任务'
	ai.thinking = ''
	ai.text = ''
	ai.tools = []
	ai.result = null
	ai.clarification = null
	ai.sourceMessageId = confirmation ? null : pending.sourceMessageId
	ai.recoveryPlaceholder = false
	conversationStore.currentSession.requestId = requestId
	conversationStore.currentSession.status = 'QUEUED'
	conversationStore.setActiveMessageId(requestId)
	conversationStore.setStreaming(true)
	const streamPayload = {
		requestId,
		conversationId: pending.conversationId,
		modelId: pending.modelId || conversationStore.currentSession.modelId || undefined,
		imageModelId: pending.imageModelId || conversationStore.currentSession.imageModelId || undefined,
		skill: pending.skill,
		clarificationScopeId: pending.clarificationScopeId,
		clarificationOptionId: pending.clarificationOptionId,
	}
	if (confirmation) streamPayload.message = confirmationText
	else streamPayload.sourceMessageId = pending.sourceMessageId
	// A disconnect recovers this new run; it must not auto-submit the paid confirmation twice.
	conversationStore.clearPendingResumeTask(pending.conversationId)
	scrollToBottom()
	const submitStream = confirmation ? chatStream : resumeChatStream
	streams.set(String(streamPayload.conversationId), submitStream(streamPayload, createStreamHandlers(ai, streamPayload)))
}

// 合并工具帧:同名工具 running→done 就地更新,否则追加
function mergeTool(ai, e) {
	if (!e || !e.name) return
	const exist = ai.tools.find((t) => t.name === e.name)
	if (exist) {
		exist.status = e.status || exist.status
		if (e.summary) exist.summary = e.summary
		if (e.n != null) exist.n = e.n
		if (e.taskId) exist.taskId = e.taskId
	} else {
		ai.tools.push({
			name: e.name,
			status: e.status || 'running',
			summary: e.summary || '',
			n: e.n,
			taskId: e.taskId,
		})
	}
}

// 由 result 数据构造可渲染结构(图片补展示字段)
function buildResult(data) {
	const images = Array.isArray(data.images)
		? data.images.map((img) => ({
				taskId: img.taskId,
				seq: img.seq,
				status: 'RUNNING',
				url: '',
				fullUrl: '',
				previewUrl: '',
		  }))
		: []
	return {
		title: data.title || '',
		body: data.body || '',
		tags: Array.isArray(data.tags) ? data.tags : [],
		images,
	}
}

// 合并早到的工具任务与最终结构化结果，避免停止流后丢失已提交图片任务。
function mergeResultData(current, data) {
	const incoming = buildResult(data || {})
	if (!current) return incoming
	if (incoming.title) current.title = incoming.title
	if (incoming.body) current.body = incoming.body
	if (incoming.tags.length) current.tags = incoming.tags
	if (incoming.images.length) {
		const previous = Array.isArray(current.images) ? current.images : []
		const additions = incoming.images.filter((img) => !previous.some(
			(item) => item.taskId === img.taskId && Number(item.seq) === Number(img.seq)
		))
		current.images = previous.concat(additions)
	}
	return current
}

// Only an explicit stop targets the current session; other sessions continue.
async function handleStop() {
	const id = conversationStore.conversationId
	const session = conversationStore.getSession(id)
	const runId = session.requestId || session.activeMessageId
	if (!runId) return
	try {
		await stopChat(runId)
		streams.get(String(id))?.abort?.()
		finishStream(id, session.requestId)
		session.recovering = true
		startConversationSync(id)
	} catch { uni.showToast({ title: '停止失败，请重试', icon: 'none' }) }
}
function finishStream(id, requestId) {
	const session = conversationStore.getSession(id)
	if (session.requestId !== requestId) return
	session.streaming = false
	streams.delete(String(id))
	conversationStore.persist()
}

// 刷新余额(onDone / 出图完成后)
async function refreshBalance() {
	// 未登录不请求余额:避免游客触发 401 → forceLogout 被踢登录
	if (!userStore.isLoggedIn) return
	try {
		const data = await getBalance()
		const balance = typeof data === 'number' ? data : data && data.balance
		if (balance != null) accountStore.setBalance(balance)
	} catch (e) {
		// 刷新失败不打断流程;dev 下打印便于排查
		if (process.env.NODE_ENV === 'development') {
			console.warn('[余额] 刷新失败', e)
		}
	}
}

// ====== 出图轮询 ======
// 对 result.images 每个 {taskId,seq} 轮询 getGeneration,回填 status/fullUrl
function startResultPolling(result) {
	if (!result || !Array.isArray(result.images) || !result.images.length) return
	// 按 taskId 去重轮询(一个 task 含多张,seq 对应 resultUrls 下标)
	const taskIds = [...new Set(result.images.filter((img) => !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(img.status)).map((img) => img.taskId).filter(Boolean))]
	taskIds.forEach((taskId) => pollTask(taskId, result))
}

function clearResultPolling() {
	pollTimers.forEach((timer) => clearTimeout(timer))
	pollTimers.clear()
	pollingTasks.clear()
}

function pollTask(taskId, result) {
	if (!taskId || disposed) return
	if (pollingTasks.has(taskId)) { pollingTasks.get(taskId).add(result); return }
	const subscribers = new Set([result])
	pollingTasks.set(taskId, subscribers)
	const update = (...args) => subscribers.forEach((target) => markTaskImages(target, taskId, ...args))
	const INTERVAL = 3000

	const tick = async () => {
		if (disposed || pollingTasks.get(taskId) !== subscribers) return
		try {
			const task = await getGeneration(taskId, { showError: false })
			if (disposed || pollingTasks.get(taskId) !== subscribers) return
			const status = task && task.status
			if (status === 'SUCCEEDED') {
				update('SUCCEEDED', task.resultUrls || [], task.previewUrls || [])
				pollingTasks.delete(taskId)
				refreshBalance()
				return
			}
			if (status === 'RUNNING' && task.resultUrls) {
				update('RUNNING', task.resultUrls, task.previewUrls || [])
			}
			if (status === 'FAILED' || status === 'CANCELLED') {
				update('FAILED', task.resultUrls || [], task.previewUrls || [])
				refreshBalance()
				pollingTasks.delete(taskId)
				return
			}
		} catch (e) {
			// 单次查询失败,继续重试
		}
		if (disposed || pollingTasks.get(taskId) !== subscribers) return
		const timer = setTimeout(() => {
			pollTimers.delete(timer)
			tick()
		}, INTERVAL)
		pollTimers.add(timer)
	}
	tick()
}

// 只替换当前失败槽位，新任务按单张 seq=1 轮询后回填。
async function retryFailedSlot(result, payload) {
	if (!result || !payload || !payload.image || payload.image.retrying) return
	const target = payload.image
	if (target.status !== 'FAILED') return
	target.retrying = true
	try {
		const task = await retryFailedImage(target.taskId, target.seq)
		if (!task || !task.taskId) throw new Error('重试任务创建失败')
		target.taskId = task.taskId
		target.seq = 1
		target.status = task.status || 'QUEUED'
		target.url = ''
		target.fullUrl = ''
		target.previewUrl = ''
		pollTask(task.taskId, result)
	} catch (e) {
		const data = e && e.data
		const rechargeRequired =
			(e && e.errorCode === 'INSUFFICIENT_BEANS') ||
			(data && data.errorCode === 'INSUFFICIENT_BEANS')
		if (rechargeRequired) {
			uni.showModal({
				title: '豆子余额不足',
				content: (e && e.message) || '请先充值后重试',
				confirmText: '去充值',
				success: (res) => {
					if (res.confirm) goRecharge()
				},
			})
		} else {
			uni.showToast({ title: (e && e.message) || '重试失败', icon: 'none' })
		}
	} finally {
		target.retrying = false
	}
}

function copyUserMessage(msg) {
	if (!msg || !msg.content) return
	uni.setClipboardData({
		data: msg.content,
		success: () => uni.showToast({ title: '已复制', icon: 'none' }),
	})
}

function favoriteUserMessage(msg) {
	if (!msg || !msg.content || favoriteSaving.value) return
	if (!requireLogin()) return
	const content = msg.content.trim()
	if (!content) return
	uni.showModal({
		title: '收藏提示词',
		editable: true,
		placeholderText: '请输入备注名称',
		confirmText: '收藏',
		success: async (res) => {
			if (!res.confirm) return
			const name = (res.content || '').trim()
			if (!name) {
				uni.showToast({ title: '请输入备注名称', icon: 'none' })
				return
			}
			if (Array.from(name).length > 50) {
				uni.showToast({ title: '备注名称最多50个字符', icon: 'none' })
				return
			}
			favoriteSaving.value = true
			try {
				await createPromptFavorite(name, content)
				uni.showToast({ title: '收藏成功', icon: 'success' })
			} catch (e) {
				// 业务错误由请求层统一提示。
			} finally {
				favoriteSaving.value = false
			}
		},
	})
}

// 编辑只恢复到输入区，不改动历史消息。
function editUserMessage(msg) {
	if (!canEditMessage.value || !msg) return
	// 编辑历史消息表示发起新任务，不应误绑定上一个待回答问题。
	pendingClarification.value = null
	selectedClarificationOptionId.value = ''
	setInputText(msg.content || '')
	pendingImages.value = Array.isArray(msg.images)
		? msg.images.map((img) =>
				typeof img === 'string' ? { type: 'upload', url: img } : { ...img }
		  )
		: []
}

// 收藏页只回填当前会话输入框；清除旧追问绑定，保留待发送图片和已激活 Skill。
function applyPromptDraft() {
	const draft = conversationStore.takePromptDraft()
	if (!draft) return
	pendingClarification.value = null
	selectedClarificationOptionId.value = ''
	setInputText(draft)
	plusVisible.value = false
}

// 根据实际渲染高度判断是否超过 6 行，只折叠用户消息。
function measureUserMessages() {
	nextTick(() => {
		setTimeout(() => {
			const userMessages = messages.value.filter((msg) => msg.role === 'user' && msg.content)
			if (!userMessages.length) return
			const query = uni.createSelectorQuery()
			if (componentInstance && componentInstance.proxy) query.in(componentInstance.proxy)
			query
				.selectAll('.user-text')
				.boundingClientRect((rects) => {
					const maxHeight = uni.upx2px(48 * 6) + 1
					;(rects || []).forEach((rect, index) => {
						if (userMessages[index] && !userMessages[index].collapsible) {
							userMessages[index].collapsible = rect.height > maxHeight
						}
					})
				})
				.exec()
		}, 30)
	})
}

// 回填某 taskId 下所有图片的状态与地址
function markTaskImages(result, taskId, status, resultUrls, previewUrls = []) {
	if (!result || !Array.isArray(result.images)) return
	result.images.forEach((img) => {
		if (img.taskId !== taskId) return
		if ((status === 'SUCCEEDED' || status === 'RUNNING' || status === 'FAILED') && resultUrls) {
			const url = resultUrls[(img.seq || 1) - 1]
			if (url) {
				img.status = 'SUCCEEDED'
				img.url = url
				img.fullUrl = fullUrl(url)
				const preview = previewUrls[(img.seq || 1) - 1]
				img.previewUrl = fullUrl(preview || url)
			} else if (status === 'SUCCEEDED' || status === 'FAILED') {
				img.status = 'FAILED'
			}
		} else {
			img.status = status
		}
	})
}

// ====== 生命周期 ======

function handleResumeReady() {
	setTimeout(() => tryResumePendingTask(), 80)
}

watch(
	() => conversationStore.promptDraft,
	(draft) => {
		if (draft) applyPromptDraft()
	},
	{ immediate: true }
)

watch(() => userStore.userInfo?.id || userStore.userInfo?.userId || '', () => {
	if (!conversationStore.syncOwner()) return
	sessionEpoch += 1
	historySwitchVersion += 1
	conversationListVersion += 1
	historyLoading.value = false
	preparingConversation.value = false
	historySwitchingId.value = null
	newConversationSwitching.value = false
	conversationRecords.value = []
	streams.forEach((stream) => stream?.abort?.())
	streams.clear()
	clearConversationSync()
	clearResultPolling()
	chatModels.value = []; imageModels.value = []; modelsLoading.value = false
	restoreInputDraft()
	loadModels()
})

onMounted(async () => {
	conversationStore.syncOwner()
	loadModels()
	uni.$on('agent-resume-ready', handleResumeReady)
	scheduleInputMeasure()
	scheduleDockMeasure()
	// 挂载拉取余额
	refreshBalance()
	// 拉取唯一可用 skill
	loadSkill()
	// 恢复上次打开的会话；若最后一条仍是用户消息，则等待服务端落最终结果。
	if (userStore.isLoggedIn && conversationStore.conversationId != null) {
		await loadConversation(conversationStore.conversationId)
	}
	tryResumePendingTask()
})

onBeforeUnmount(() => {
	saveInputDraft()
	disposed = true
	cancelReferenceUpload()
	uni.$off('agent-resume-ready', handleResumeReady)
	// 页面关闭只断开本地连接，不等同于用户点击停止；服务端任务继续执行。
	historySwitchVersion += 1
	clearHistoryMarkdownWait()
	clearConversationSync()
	clearResultPolling()
	scrollCommandVersion += 1
	scrollTopCommandVersion += 1
	obstructionMeasureVersion += 1
	if (scrollReleaseTimer) clearTimeout(scrollReleaseTimer)
	scrollReleaseTimer = null
	inputMeasureVersion += 1
	clearInputMeasureTimer()
	dockMeasureVersion += 1
	clearScrollInsetTimer()
	clearKeyboardCloseFallback()
	clearKbCollapseTimer()
	clearKbCoalesce()
	dockMotionVersion += 1
	clearDockMotionTimers()
	streams.forEach((stream) => stream?.abort?.())
	streams.clear()
	Object.values(conversationStore.sessions).forEach((session) => { session.streaming = false })
	conversationStore.persist()
})
</script>

<style lang="scss" scoped>
.recharge-recovery { display: flex; align-items: center; gap: 20rpx; margin-top: 18rpx; }
.recharge-recovery-hint { font-size: 24rpx; color: #8792a5; }
.recharge-recovery-action { font-size: 26rpx; color: #477ede; padding: 10rpx 20rpx; background: #eef4ff; border-radius: 24rpx; }

.chat-content {
	height: 100%;
	background: #f6f7fb;
	position: relative;
	overflow: hidden;
	display: flex;
	flex-direction: column;
}

/* ====== 滚动区 ====== */
.page-scroll {
	height: auto;
	flex: 1;
	min-height: 0;
}

.scroll-anchor {
	/* 兜底留白;运行时恒被内联 scrollPadBottom(Dock 实测高度 + 键盘 inset)覆盖 */
	padding-bottom: calc(100rpx + 320rpx + env(safe-area-inset-bottom));
	padding-left: 36rpx;
	padding-right: 36rpx;
	box-sizing: border-box;
}

.scroll-bottom-anchor {
	height: 1rpx;
}

/* ====== 空态 ====== */
.empty-state {
	position: absolute;
	top: 40rpx;
	left: 36rpx;
	right: 36rpx;
	z-index: 1;
	display: flex;
	flex-direction: column;
	padding-top: 64rpx;
	pointer-events: none;
}

.empty-kicker {
	font-size: 24rpx;
	letter-spacing: 6rpx;
	color: #8fa8dc;
	line-height: 1;
}

.empty-title {
	display: flex;
	flex-direction: column;
	margin-top: 32rpx;
}

.empty-line {
	font-size: 64rpx;
	font-weight: 400;
	color: #15161b;
	line-height: 88rpx;
	letter-spacing: -1rpx;
}

.empty-accent {
	font-weight: 700;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
	-webkit-background-clip: text;
	background-clip: text;
	-webkit-text-fill-color: transparent;
}

.empty-sub {
	margin-top: 32rpx;
	font-size: 28rpx;
	color: #9499a6;
	line-height: 1.7;
}

/* ====== 消息流 ====== */
.msg-list {
	display: flex;
	flex-direction: column;
	gap: 32rpx;
}

.history-older-loading {
	padding: 8rpx 0 16rpx;
	text-align: center;
}

.history-older-loading-text {
	font-size: 23rpx;
	color: #9aa3b2;
}

/* 用户气泡(右) */
.user-row {
	display: flex;
	justify-content: flex-end;
}

.user-bubble {
	max-width: 76%;
	padding: 24rpx 28rpx;
	border-radius: 32rpx 32rpx 8rpx 32rpx;
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
}

.user-imgs {
	display: flex;
	flex-wrap: wrap;
	gap: 12rpx;
	margin-bottom: 12rpx;
}

.user-img-wrap {
	position: relative;
	width: 140rpx;
	height: 140rpx;
}

.user-img {
	width: 100%;
	height: 100%;
	border-radius: 16rpx;
}

.user-img-index,
.dock-img-index {
	position: absolute;
	left: 8rpx;
	bottom: 8rpx;
	padding: 2rpx 10rpx;
	border-radius: 12rpx;
	background: rgba(0, 0, 0, 0.58);
	font-size: 20rpx;
	line-height: 1.4;
	color: #fff;
	pointer-events: none;
}

.user-text {
	display: block;
	font-size: 30rpx;
	line-height: 1.6;
	color: #fff;
	white-space: pre-wrap;
	word-break: break-word;
}

.user-text--collapsed {
	max-height: 288rpx;
	overflow: hidden;
}

.user-actions {
	display: flex;
	justify-content: flex-end;
	gap: 24rpx;
	margin-top: 12rpx;
	padding-top: 10rpx;
	border-top: 1rpx solid rgba(255, 255, 255, 0.25);
}

.user-action {
	font-size: 23rpx;
	line-height: 1.4;
	color: rgba(255, 255, 255, 0.92);
}

/* AI 块(左):去逐条头像,整行铺满 */
.ai-row {
	display: flex;
}

.ai-body {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 20rpx;
	padding-top: 4rpx;
}

.ai-text,
.ai-md {
	font-size: 30rpx;
	line-height: 1.7;
	color: #1e293b;
	word-break: break-word;
}

.ai-text {
	white-space: pre-wrap;
}

.ai-stopped {
	display: flex;
	flex-direction: column;
	gap: 6rpx;
	padding: 18rpx 22rpx;
	border-radius: 16rpx;
	background: #f1f5f9;
}

.ai-stopped-title {
	font-size: 26rpx;
	font-weight: 600;
	color: #64748b;
}

.ai-stopped-sub {
	font-size: 24rpx;
	color: #4f84ea;
}

/* 等待首帧:三点跳动 */
.ai-waiting {
	display: flex;
	align-items: center;
	gap: 10rpx;
	height: 48rpx;
}

.ai-waiting-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #c7d6f2;
	animation: ai-wait 1.2s infinite ease-in-out;
}

.ai-waiting-dot:nth-child(2) {
	animation-delay: 0.2s;
}

.ai-waiting-dot:nth-child(3) {
	animation-delay: 0.4s;
}

@keyframes ai-wait {
	0%,
	60%,
	100% {
		opacity: 0.3;
		transform: translateY(0);
	}
	30% {
		opacity: 1;
		transform: translateY(-6rpx);
	}
}

/* ====== 底部输入区 ====== */
.chat-models {
	display: flex;
	gap: 12rpx;
}

.chat-model-slot {
	flex: 1;
	min-width: 0;
}

.dock {
	position: fixed;
	left: 36rpx;
	right: 36rpx;
	bottom: 140rpx;
	bottom: calc(140rpx + constant(safe-area-inset-bottom));
	bottom: calc(140rpx + env(safe-area-inset-bottom));
	z-index: 6;
	display: flex;
	flex-direction: column;
	gap: 16rpx;
	/* 时长由内联 transitionDuration 控制:iOS 取键盘事件 duration,安卓恒 0(直接落位)。
	   曲线取 iOS 键盘动画的社区近似值,让 Dock 贴着键盘走,而不是一段独立的 ease-out。
	   不设常驻 will-change:动画结束后 Dock 固化为纯 bottom 定位,
	   编辑期若仍处于合成层/transform 状态,原生 textarea 点击定位会失准。 */
	transition-property: transform;
	transition-timing-function: cubic-bezier(0.38, 0.7, 0.125, 1);
}

.pending-clarification-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20rpx;
	padding: 18rpx 22rpx;
	border: 1rpx solid rgba(79, 132, 234, 0.22);
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.96);
	box-shadow: 0 8rpx 24rpx rgba(55, 90, 150, 0.08);
}

.pending-clarification-copy {
	min-width: 0;
	display: flex;
	flex: 1;
	flex-direction: column;
	gap: 4rpx;
}

.pending-clarification-title {
	font-size: 22rpx;
	font-weight: 700;
	color: #4f84ea;
}

.pending-clarification-question {
	max-width: 100%;
	font-size: 23rpx;
	line-height: 1.4;
	color: #65748a;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.pending-clarification-cancel {
	flex-shrink: 0;
	font-size: 23rpx;
	font-weight: 600;
	color: #718096;
}

/* 单 Skill 激活按钮:未激活=白底描边,已激活=浅蓝高亮 */
.skill-chip {
	align-self: flex-start;
	display: flex;
	align-items: center;
	gap: 10rpx;
	height: 52rpx;
	padding: 0 18rpx;
	border-radius: 26rpx;
	background: #fff;
	border: 1rpx solid #edeef2;
}

.skill-chip:active {
	opacity: 0.9;
}

.skill-chip--active {
	background: #deeafb;
	border-color: rgba(96, 140, 224, 0.2);
}

.skill-chip-star {
	font-size: 24rpx;
	color: #94a3b8;
}

.skill-chip--active .skill-chip-star {
	color: #4f84ea;
}

.skill-chip-name {
	font-size: 24rpx;
	font-weight: 600;
	color: #64748b;
}

.skill-chip--active .skill-chip-name {
	color: #4f84ea;
}

.skill-chip-state {
	font-size: 22rpx;
	font-weight: 600;
	color: #4f84ea;
	background: rgba(255, 255, 255, 0.7);
	border-radius: 12rpx;
	padding: 2rpx 12rpx;
	line-height: 1.4;
}

/* 已附带图片缩略图 */
.dock-imgs {
	width: 100%;
	height: 120rpx;
	white-space: nowrap;
}

.dock-img-track {
	display: inline-flex;
	gap: 16rpx;
}

.dock-img {
	position: relative;
	width: 120rpx;
	height: 120rpx;
	border-radius: 20rpx;
	overflow: hidden;
}

.dock-img-el {
	width: 100%;
	height: 100%;
}

.dock-img-del {
	position: absolute;
	top: 4rpx;
	right: 4rpx;
	width: 34rpx;
	height: 34rpx;
	border-radius: 50%;
	background: rgba(0, 0, 0, 0.6);
	display: flex;
	align-items: center;
	justify-content: center;
	z-index: 2;
}

.dock-img-del-x {
	font-size: 26rpx;
	color: #fff;
	line-height: 1;
}

/* 编辑器恒为三段式：上传、纯文字输入、发送。文字换行时宽度与两侧按钮位置均不变化。 */
.input-row {
	width: 100%;
	display: flex;
	align-items: flex-end;
	gap: 0;
	border: 2rpx solid #dfe4ed;
	border-radius: 30rpx;
	background: #fff;
	box-sizing: border-box;
}

.input-row--focused {
	border-color: #a8c4f5;
}

.input-plus-wrap {
	position: relative;
	width: 88rpx;
	height: 88rpx;
	flex-shrink: 0;
}

.input-plus {
	width: 88rpx;
	height: 88rpx;
	background: transparent;
	border: 0;
	box-shadow: none;
	display: flex;
	align-items: center;
	justify-content: center;
	box-sizing: border-box;
}

.input-plus-surface {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.12s ease, transform 0.12s ease;
}

.input-plus:active .input-plus-surface {
	background: #f1f3f6;
	transform: scale(0.96);
}

.input-plus-icon {
	font-size: 40rpx;
	color: #15161b;
	line-height: 1;
}

.input-surface {
	position: relative;
	flex: 1;
	min-width: 0;
	min-height: 86rpx;
	background: transparent;
	border: 0;
	box-shadow: none;
	box-sizing: border-box;
	overflow: hidden;
}

/* 与 textarea 使用完全相同的内容宽度和排版，只负责测量已写入 value 的文本高度。 */
.input-measure {
	position: absolute;
	top: 22rpx;
	left: 24rpx;
	right: 24rpx;
	z-index: 0;
	display: block;
	min-height: 42rpx;
	font-size: 30rpx;
	line-height: 42rpx;
	white-space: pre-wrap;
	word-break: break-all;
	overflow-wrap: anywhere;
	color: transparent;
	opacity: 0;
	pointer-events: none;
}

.input-field {
	position: absolute;
	top: 0;
	right: 0;
	bottom: 0;
	left: 0;
	z-index: 1;
	width: 100%;
	min-width: 0;
	min-height: 86rpx;
	padding: 22rpx 24rpx;
	font-size: 30rpx;
	color: #15161b;
	line-height: 42rpx;
	box-sizing: border-box;
	background: transparent;
	overflow-y: auto;
	word-break: break-all;
}

.input-ph {
	color: #b3b7c2;
}

/* 发送/停止按钮与上传按钮各占固定槽位，长文本仅推动输入区上边缘。 */
.send-btn {
	width: 88rpx;
	height: 88rpx;
	flex-shrink: 0;
	background: transparent;
	display: flex;
	align-items: center;
	justify-content: center;
}

.send-btn-surface {
	width: 64rpx;
	height: 64rpx;
	border-radius: 50%;
	background: #eef1f5;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.12s ease, transform 0.12s ease;
}

.send-btn--active .send-btn-surface {
	background: linear-gradient(135deg, #4f84ea, #8fb4fb);
}

.send-btn:active .send-btn-surface {
	transform: scale(0.94);
}

.send-btn--stop .send-btn-surface {
	background: #eef2f8;
}

.send-icon {
	font-size: 38rpx;
	font-weight: 700;
	color: #9aa3b2;
	line-height: 1;
}

.send-btn--active .send-icon {
	color: #fff;
}

.stop-square {
	width: 24rpx;
	height: 24rpx;
	border-radius: 6rpx;
	background: #ef4444;
}
</style>
