<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">‹</view>
				<text class="nav-title">提现详情</text>
				<view class="nav-space" />
			</view>
		</view>
		<scroll-view class="scroll" scroll-y>
			<view class="content" :style="{ paddingTop: (statusBarHeight + 66) + 'px' }">
				<view class="status-card">
					<view class="status-icon" :class="statusClass(detail.status)">{{ statusIcon(detail.status) }}</view>
					<text class="status-title">{{ statusText(detail.status) }}</text>
					<text class="amount">¥{{ money(detail.applyAmount) }}</text>
					<text v-if="detail.status === 'NEEDS_REVIEW'" class="status-desc">关联订单发生退款，该提现已暂停打款，后台将驳回并重新计算余额。</text>
					<text v-else-if="detail.status === 'SUBMITTED'" class="status-desc">后台正在审核，通过后将线下打款。</text>
				</view>

				<view class="detail-card">
					<view class="row"><text>提现单号</text><text>{{ detail.withdrawalNo || '--' }}</text></view>
					<view class="row"><text>收款方式</text><text>{{ methodText(detail.methodType) }}</text></view>
					<view class="row"><text>收款人</text><text>{{ detail.payeeName || '--' }}</text></view>
					<view v-if="detail.methodType === 'BANK_CARD'" class="row"><text>开户银行</text><text>{{ detail.bankName || '--' }}</text></view>
					<view v-if="detail.methodType === 'BANK_CARD'" class="row"><text>银行卡</text><text>{{ detail.bankAccountMasked || '****' }}</text></view>
					<view v-else class="row"><text>收款码</text><text>{{ detail.receiptAvailable ? '已安全留存' : '--' }}</text></view>
					<view class="row"><text>申请时间</text><text>{{ formatTime(detail.applyTime) }}</text></view>
					<view v-if="detail.auditTime" class="row"><text>审核时间</text><text>{{ formatTime(detail.auditTime) }}</text></view>
					<view v-if="detail.paidTime" class="row"><text>打款时间</text><text>{{ formatTime(detail.paidTime) }}</text></view>
					<view v-if="detail.paymentReferenceMasked" class="row"><text>付款流水号</text><text>{{ detail.paymentReferenceMasked }}</text></view>
					<view v-if="detail.status === 'PAID'" class="row"><text>付款凭证</text><text>{{ detail.paymentProofAvailable ? '已由平台私有留档' : '--' }}</text></view>
					<view v-if="detail.rejectReason" class="row reason"><text>驳回原因</text><text>{{ detail.rejectReason }}</text></view>
				</view>
				<text class="privacy-tip">完整银行卡、收款码及付款凭证均在私有存储中保护，此页仅展示脱敏信息。</text>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDistributionWithdrawal } from '@/api/distribution.js'

const statusBarHeight = ref(44)
const detail = reactive({})
const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

onLoad(async (query) => {
	if (!query?.id) return
	Object.assign(detail, await getDistributionWithdrawal(query.id))
})

function money(value) { return ((Number(value) || 0) / 100).toFixed(2) }
function methodText(value) { return { WECHAT_QR: '微信收款码', ALIPAY_QR: '支付宝收款码', BANK_CARD: '银行卡' }[value] || value || '--' }
function statusText(value) { return { SUBMITTED: '待审核', NEEDS_REVIEW: '需复核', PAID: '已打款', REJECTED: '已驳回' }[value] || '加载中' }
function statusIcon(value) { return value === 'PAID' ? '✓' : (value === 'REJECTED' || value === 'NEEDS_REVIEW' ? '!' : '···') }
function statusClass(value) { return value === 'PAID' ? 'success' : (value === 'REJECTED' || value === 'NEEDS_REVIEW' ? 'danger' : 'pending') }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '--' }
function goBack() { uni.navigateBack() }
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f6fa; color: #172033; }
.nav { position: fixed; z-index: 10; top: 0; left: 0; right: 0; background: #fff; }
.nav-inner { height: 104rpx; padding: 0 28rpx; display: flex; align-items: center; }
.nav-back, .nav-space { width: 100rpx; }
.nav-back { color: #4f84ea; font-size: 60rpx; line-height: 1; }
.nav-title { flex: 1; text-align: center; font-size: 34rpx; font-weight: 650; }
.scroll { height: 100vh; }
.content { padding-left: 28rpx; padding-right: 28rpx; padding-bottom: 60rpx; }
.status-card, .detail-card { padding: 34rpx; border-radius: 28rpx; background: #fff; }
.status-card { text-align: center; }
.status-icon { width: 82rpx; height: 82rpx; margin: 0 auto; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 38rpx; font-weight: 700; }
.pending { color: #9a6a18; background: #fff3d9; }
.success { color: #25845a; background: #e8f7ef; }
.danger { color: #c65151; background: #ffeded; }
.status-title, .amount, .status-desc { display: block; }
.status-title { margin-top: 20rpx; font-size: 31rpx; font-weight: 650; }
.amount { margin-top: 18rpx; color: #3f70d7; font-size: 52rpx; font-weight: 700; }
.status-desc { margin-top: 20rpx; color: #7e8798; font-size: 23rpx; line-height: 1.55; }
.detail-card { margin-top: 22rpx; }
.row { display: flex; justify-content: space-between; gap: 30rpx; padding: 23rpx 0; border-bottom: 2rpx solid #f0f2f5; color: #81899a; font-size: 24rpx; }
.row:last-child { border-bottom: 0; }
.row text:last-child { max-width: 65%; color: #2b3446; text-align: right; word-break: break-all; }
.reason text:last-child { color: #c65151; }
.privacy-tip { display: block; padding: 24rpx 12rpx; color: #9299a8; font-size: 22rpx; line-height: 1.55; }
</style>
