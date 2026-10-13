<template>
	<view class="pay-page">
		<!-- 自定义导航栏 -->
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">收银台</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<!-- 内容区域 -->
		<view class="content" :style="{ paddingTop: (statusBarHeight + 44) + 'px' }">
			<!-- 金额展示 -->
			<view class="amount-card">
				<text class="amount-label">支付金额</text>
				<view class="amount-value">
					<text class="amount-symbol">¥</text>
					<text class="amount-number">{{ amountYuan }}</text>
				</view>
				<text class="amount-subject">{{ subject }}</text>
			</view>

			<!-- 支付方式（当前仅微信支付） -->
			<view class="card-group">
				<view class="pay-item">
					<view class="pay-item-left">
						<text class="pay-icon">微</text>
						<text class="pay-name">微信支付</text>
					</view>
					<text class="pay-checked">✓</text>
				</view>
			</view>
		</view>

		<!-- 底部支付按钮 -->
		<view class="footer">
			<!-- #ifdef MP-WEIXIN -->
			<button class="pay-btn" :class="{ disabled: paying }" :disabled="paying" @tap="handlePay">
				{{ paying ? '支付中...' : `确认支付 ¥${amountYuan}` }}
			</button>
			<!-- #endif -->
			<!-- #ifndef MP-WEIXIN -->
			<button class="pay-btn disabled" disabled @tap="handleAppTip">
				请在微信小程序中支付
			</button>
			<!-- #endif -->
		</view>
	</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createOrder, queryOrder } from '@/api/pay.js'

// 支付金额（单位：分），默认 1 分用于演示，可由页面参数覆盖
const amount = ref(1)
// 订单描述
const subject = ref('Aurora 支付演示')
// 支付中标志，防重复点击
const paying = ref(false)

// 状态栏高度
const statusBarHeight = ref(44)
uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	}
})

// 分转元展示
const amountYuan = computed(() => (amount.value / 100).toFixed(2))

// 页面参数：amount(分)、subject
onLoad((options) => {
	if (options && options.amount) {
		const n = parseInt(options.amount, 10)
		if (!isNaN(n) && n > 0) amount.value = n
	}
	if (options && options.subject) {
		subject.value = decodeURIComponent(options.subject)
	}
})

/** 返回上一页 */
function goBack() {
	uni.navigateBack()
}

// #ifdef MP-WEIXIN
/** 发起支付（小程序端） */
async function handlePay() {
	if (paying.value) return
	paying.value = true
	uni.showLoading({ title: '下单中...', mask: true })
	try {
		// 1. 后端创建订单 + 下单，拿到支付参数
		const res = await createOrder({ amount: amount.value, subject: subject.value })
		const orderNo = res.orderNo
		const payParams = res.payParams

		uni.hideLoading()

		// 2. 调起微信支付（V3：signType 固定 RSA，package 键名固定）
		await new Promise((resolve, reject) => {
			uni.requestPayment({
				provider: 'wxpay',
				timeStamp: payParams.timeStamp,
				nonceStr: payParams.nonceStr,
				package: payParams.package,
				signType: payParams.signType,
				paySign: payParams.paySign,
				success: resolve,
				fail: reject
			})
		})

		// 3. 兜底查单：回调可能延迟，syncWechat=1 强制同步微信确认
		uni.showLoading({ title: '确认中...', mask: true })
		const order = await queryOrder(orderNo, 1)
		uni.hideLoading()

		if (order && order.status === 1) {
			uni.showToast({ title: '支付成功', icon: 'success' })
		} else {
			uni.showToast({ title: '支付结果确认中，请稍后查看', icon: 'none' })
		}
	} catch (e) {
		uni.hideLoading()
		// 用户主动取消支付
		if (e && e.errMsg && e.errMsg.indexOf('cancel') !== -1) {
			uni.showToast({ title: '已取消支付', icon: 'none' })
		} else {
			// 下单失败等错误已由 request 统一提示，此处兜底
			uni.showToast({ title: '支付失败，请重试', icon: 'none' })
		}
	} finally {
		paying.value = false
	}
}
// #endif

// #ifndef MP-WEIXIN
/** 非小程序端占位提示 */
function handleAppTip() {
	uni.showToast({ title: '请在微信小程序中完成支付', icon: 'none' })
}
// #endif
</script>

<style lang="scss" scoped>
.pay-page {
	min-height: 100vh;
	background-color: #F5F6FA;
}

/* ====== 自定义导航栏 ====== */
.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background-color: #FFFFFF;
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
	color: #1A1A1A;
	font-weight: 300;
}

.nav-title {
	font-size: 34rpx;
	font-weight: 600;
	color: #1A1A1A;
}

.nav-placeholder {
	width: 72rpx;
}

/* ====== 内容区域 ====== */
.content {
	box-sizing: border-box;
}

/* ====== 金额卡片 ====== */
.amount-card {
	margin: 24rpx;
	padding: 48rpx 32rpx;
	background-color: #FFFFFF;
	border-radius: 20rpx;
	display: flex;
	flex-direction: column;
	align-items: center;
}

.amount-label {
	font-size: 26rpx;
	color: #999999;
}

.amount-value {
	display: flex;
	align-items: baseline;
	margin-top: 16rpx;
}

.amount-symbol {
	font-size: 40rpx;
	color: #1A1A1A;
	font-weight: 600;
}

.amount-number {
	font-size: 72rpx;
	color: #1A1A1A;
	font-weight: 700;
	margin-left: 4rpx;
}

.amount-subject {
	font-size: 28rpx;
	color: #666666;
	margin-top: 20rpx;
}

/* ====== 卡片分组 ====== */
.card-group {
	background-color: #FFFFFF;
	margin: 0 24rpx;
	border-radius: 20rpx;
	overflow: hidden;
}

/* ====== 支付方式项 ====== */
.pay-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 28rpx 32rpx;
}

.pay-item-left {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.pay-icon {
	width: 56rpx;
	height: 56rpx;
	line-height: 56rpx;
	text-align: center;
	border-radius: 12rpx;
	background-color: #07C160;
	color: #FFFFFF;
	font-size: 28rpx;
}

.pay-name {
	font-size: 30rpx;
	color: #1A1A1A;
}

.pay-checked {
	font-size: 34rpx;
	color: #07C160;
	font-weight: 600;
}

/* ====== 底部支付按钮 ====== */
.footer {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	padding: 20rpx 24rpx;
	padding-bottom: calc(20rpx + constant(safe-area-inset-bottom));
	padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
	background-color: #FFFFFF;
}

.pay-btn {
	width: 100%;
	height: 92rpx;
	line-height: 92rpx;
	background-color: #4C6EF5;
	color: #FFFFFF;
	font-size: 32rpx;
	font-weight: 500;
	border-radius: 46rpx;
	border: none;

	&::after {
		border: none;
	}
}

.pay-btn.disabled {
	background-color: #B0BEC5;
	color: #FFFFFF;
}
</style>
