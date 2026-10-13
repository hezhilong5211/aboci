<template>
	<view class="page">
		<view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="handleBack">‹</view>
				<text class="nav-title">{{ confirming ? '确认提现信息' : '佣金提现' }}</text>
				<view class="nav-space" />
			</view>
		</view>

		<scroll-view class="scroll" scroll-y @scrolltolower="loadMore">
			<view class="content" :style="{ paddingTop: (statusBarHeight + 58) + 'px' }">
				<template v-if="confirming">
					<view class="confirm-card">
						<text class="confirm-title">请核对本次提现</text>
						<text class="confirm-amount">¥{{ money(pending.applyAmount) }}</text>
						<view class="confirm-row"><text>收款方式</text><text>{{ methodText(pending.methodType) }}</text></view>
						<view class="confirm-row"><text>收款人</text><text>{{ pending.payeeName }}</text></view>
						<view v-if="pending.methodType === 'BANK_CARD'" class="confirm-row"><text>银行</text><text>{{ pending.bankName }}</text></view>
						<view v-if="pending.methodType === 'BANK_CARD'" class="confirm-row"><text>银行卡</text><text>{{ maskCard(pending.bankAccount) }}</text></view>
						<view v-else class="confirm-row"><text>收款码</text><text>已上传</text></view>
						<view class="confirm-notice">提交后该金额将被锁定。预计 1–3 个工作日内完成人工审核及线下打款，周末及法定节假日顺延，实际到账以收款渠道为准。请确保收款信息准确。</view>
					</view>
					<view class="submit-btn" :class="{ disabled: submitting }" @tap="submit">{{ submitting ? '提交中...' : '确认提交' }}</view>
				</template>

				<template v-else>
					<view class="tabs">
						<view :class="{ active: tab === 'apply' }" @tap="tab = 'apply'">申请提现</view>
						<view :class="{ active: tab === 'records' }" @tap="switchRecords">提现记录</view>
					</view>

					<template v-if="tab === 'apply'">
						<view class="balance-card">
							<text>当前可提现</text>
							<text>¥{{ money(dashboard.availableAmount) }}</text>
							<text>最低提现金额 ¥{{ money(dashboard.minWithdrawAmount) }}</text>
						</view>

						<view class="rules-card">
							<text class="rules-title">提现规则</text>
							<view class="rule-item">
								<text>可提现额度</text>
								<text>单笔最低 ¥{{ money(dashboard.minWithdrawAmount) }}，单笔最高为当前可提现余额 ¥{{ money(dashboard.availableAmount) }}</text>
							</view>
							<view class="rule-item">
								<text>每日提现次数</text>
								<text>不限，以当前可提现余额为限</text>
							</view>
							<view class="rule-item">
								<text>提现时间</text>
								<text>每天 00:00–24:00 均可申请</text>
							</view>
							<view class="rule-item">
								<text>到账时间</text>
								<text>提交后预计 1–3 个工作日内完成人工审核及线下打款，周末及法定节假日顺延，实际到账以收款渠道为准</text>
							</view>
							<view class="rules-tip">提交后金额将被锁定；资料错误或受退款影响可能被驳回，驳回后合法余额返还。</view>
						</view>

						<view class="form-card">
							<text class="field-label">提现金额</text>
							<view class="amount-input"><text>¥</text><input v-model="form.amountYuan" type="digit" placeholder="0.00" /></view>
							<text class="field-label">收款方式</text>
							<view class="method-grid">
								<view v-for="item in methods" :key="item.value" :class="{ selected: form.methodType === item.value }" @tap="form.methodType = item.value">{{ item.label }}</view>
							</view>

							<text class="field-label">收款人姓名</text>
							<input v-model="form.payeeName" class="text-input" maxlength="64" placeholder="请填写实际收款人" />

							<template v-if="form.methodType === 'BANK_CARD'">
								<text class="field-label">开户银行</text>
								<input v-model="form.bankName" class="text-input" maxlength="128" placeholder="例如：中国工商银行" />
								<text class="field-label">银行卡号</text>
								<input v-model="form.bankAccount" class="text-input" type="number" maxlength="64" placeholder="请填写收款银行卡号" />
							</template>
							<template v-else>
								<text class="field-label">{{ form.methodType === 'WECHAT_QR' ? '微信' : '支付宝' }}收款码</text>
								<view class="upload-box" @tap="chooseReceipt">
									<image v-if="form.receiptPath" :src="form.receiptPath" mode="aspectFit" />
									<text v-else>+  选择收款码图片</text>
								</view>
							</template>

							<view class="privacy-row" @tap="form.privacyAccepted = !form.privacyAccepted">
								<view class="check" :class="{ checked: form.privacyAccepted }">{{ form.privacyAccepted ? '✓' : '' }}</view>
								<text>我已单独阅读并同意收款资料用于本次提现审核、线下打款和必要的财务留档，仅限授权管理员查看。</text>
							</view>
						</view>
						<view class="submit-btn" @tap="prepareConfirm">下一步</view>
					</template>

					<template v-else>
						<view v-for="item in records" :key="item.withdrawalId" class="record-card" @tap="openDetail(item.withdrawalId)">
							<view class="record-head"><text>{{ item.withdrawalNo }}</text><text :class="statusClass(item.status)">{{ statusText(item.status) }}</text></view>
							<view class="record-main"><text>¥{{ money(item.applyAmount) }}</text><text>{{ methodText(item.methodType) }}</text></view>
							<view class="record-foot"><text>{{ formatTime(item.applyTime) }}</text><text>查看详情 ›</text></view>
						</view>
						<view v-if="!recordsLoading && records.length === 0" class="empty">暂无提现记录</view>
						<view v-else class="footer">{{ recordsLoading ? '加载中...' : (hasMore ? '上拉加载更多' : '没有更多了') }}</view>
					</template>
				</template>
			</view>
		</scroll-view>
	</view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { applyBankWithdrawal, applyQrWithdrawal, getDistributionDashboard, getDistributionWithdrawals } from '@/api/distribution.js'

const statusBarHeight = ref(44)
const tab = ref('apply')
const confirming = ref(false)
const submitting = ref(false)
const dashboard = reactive({ availableAmount: 0, minWithdrawAmount: 0 })
const form = reactive({ amountYuan: '', methodType: 'WECHAT_QR', payeeName: '', bankName: '', bankAccount: '', receiptPath: '', privacyAccepted: false })
const pending = reactive({ applyAmount: 0, methodType: '', payeeName: '', bankName: '', bankAccount: '', privacyAccepted: false })
const records = ref([])
const pageNum = ref(1)
const total = ref(0)
const recordsLoading = ref(false)
const hasMore = computed(() => records.value.length < total.value)
const methods = [
	{ value: 'WECHAT_QR', label: '微信收款码' },
	{ value: 'ALIPAY_QR', label: '支付宝收款码' },
	{ value: 'BANK_CARD', label: '银行卡' },
]
const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
statusBarHeight.value = info.statusBarHeight || 44

onLoad((query) => { if (query?.tab === 'records') tab.value = 'records' })
onShow(async () => {
	try {
		await loadDashboard()
		if (tab.value === 'records') resetRecords()
	} catch (e) {
		// 错误已由请求层提示。
	}
})

async function loadDashboard() {
	Object.assign(dashboard, await getDistributionDashboard())
}
function switchRecords() {
	tab.value = 'records'
	if (!records.value.length) loadMore()
}
function resetRecords() {
	records.value = []
	pageNum.value = 1
	total.value = 0
	loadMore()
}
async function loadMore() {
	if (tab.value !== 'records' || recordsLoading.value || (pageNum.value > 1 && !hasMore.value)) return
	recordsLoading.value = true
	try {
		const result = await getDistributionWithdrawals({ pageNum: pageNum.value, pageSize: 10 })
		records.value.push(...(result?.records || []))
		total.value = Number(result?.total) || 0
		pageNum.value += 1
	} finally {
		recordsLoading.value = false
	}
}

function chooseReceipt() {
	uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'], success: (result) => { form.receiptPath = result.tempFilePaths?.[0] || '' } })
}
function toCents(value) {
	const text = String(value || '').trim()
	if (!/^\d+(\.\d{1,2})?$/.test(text)) return null
	const [yuan, cent = ''] = text.split('.')
	return Number(yuan) * 100 + Number((cent + '00').slice(0, 2))
}
function prepareConfirm() {
	const applyAmount = toCents(form.amountYuan)
	if (!applyAmount) return toast('请输入正确的提现金额')
	if (applyAmount < Number(dashboard.minWithdrawAmount || 0)) return toast('提现金额低于最低限额')
	if (applyAmount > Number(dashboard.availableAmount || 0)) return toast('可提现余额不足')
	if (!form.payeeName.trim()) return toast('请填写收款人姓名')
	if (form.methodType === 'BANK_CARD' && (!form.bankName.trim() || !form.bankAccount.trim())) return toast('请填写完整银行卡信息')
	if (form.methodType !== 'BANK_CARD' && !form.receiptPath) return toast('请上传收款码')
	if (!form.privacyAccepted) return toast('请阅读并同意收款资料使用说明')
	Object.assign(pending, { applyAmount, methodType: form.methodType, payeeName: form.payeeName.trim(), bankName: form.bankName.trim(), bankAccount: form.bankAccount.trim(), privacyAccepted: true })
	confirming.value = true
}
async function submit() {
	if (submitting.value) return
	submitting.value = true
	try {
		if (pending.methodType === 'BANK_CARD') await applyBankWithdrawal({ ...pending })
		else await applyQrWithdrawal({ ...pending }, form.receiptPath)
		uni.showToast({ title: '提现申请已提交', icon: 'success' })
		confirming.value = false
		tab.value = 'records'
		resetForm()
		await loadDashboard()
		resetRecords()
	} catch (e) {
		// 错误已由请求或上传层提示，保留确认页便于重试。
	} finally {
		submitting.value = false
	}
}
function resetForm() {
	Object.assign(form, { amountYuan: '', methodType: 'WECHAT_QR', payeeName: '', bankName: '', bankAccount: '', receiptPath: '', privacyAccepted: false })
}
function handleBack() {
	if (confirming.value) confirming.value = false
	else uni.navigateBack()
}
function openDetail(id) { uni.navigateTo({ url: `/pages/distribution/withdraw/detail?id=${id}` }) }
function money(value) { return ((Number(value) || 0) / 100).toFixed(2) }
function maskCard(value) { const text = String(value || ''); return text.length > 8 ? `${text.slice(0, 4)} **** **** ${text.slice(-4)}` : '****' }
function methodText(value) { return { WECHAT_QR: '微信收款码', ALIPAY_QR: '支付宝收款码', BANK_CARD: '银行卡' }[value] || value }
function statusText(value) { return { SUBMITTED: '待审核', NEEDS_REVIEW: '需复核', PAID: '已打款', REJECTED: '已驳回' }[value] || value }
function statusClass(value) { return value === 'PAID' ? 'success' : (value === 'REJECTED' || value === 'NEEDS_REVIEW' ? 'danger' : 'pending') }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '--' }
function toast(title) { uni.showToast({ title, icon: 'none' }) }
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
.tabs { display: flex; margin-bottom: 22rpx; padding: 8rpx; border-radius: 24rpx; background: #e9edf5; }
.tabs view { flex: 1; padding: 20rpx 0; border-radius: 18rpx; text-align: center; color: #737c8e; font-size: 27rpx; }
.tabs .active { color: #3f6fd5; background: #fff; font-weight: 650; }
.balance-card { padding: 34rpx; border-radius: 28rpx; color: #fff; background: linear-gradient(135deg, #4f84ea, #8178ef); }
.balance-card text { display: block; }
.balance-card text:first-child { font-size: 24rpx; opacity: .86; }
.balance-card text:nth-child(2) { margin-top: 12rpx; font-size: 54rpx; font-weight: 700; }
.balance-card text:last-child { margin-top: 16rpx; font-size: 22rpx; opacity: .78; }
.rules-card { margin-top: 22rpx; padding: 30rpx 32rpx; border-radius: 28rpx; background: #fff; }
.rules-title { display: block; margin-bottom: 8rpx; color: #263044; font-size: 28rpx; font-weight: 650; }
.rule-item { display: flex; align-items: flex-start; padding-top: 20rpx; color: #6e7687; font-size: 23rpx; line-height: 1.55; }
.rule-item text:first-child { width: 148rpx; flex: none; color: #40495b; font-weight: 600; }
.rule-item text:last-child { flex: 1; }
.rules-tip { margin-top: 22rpx; padding-top: 20rpx; border-top: 2rpx solid #f0f2f5; color: #9a6a18; font-size: 22rpx; line-height: 1.55; }
.form-card, .confirm-card { margin-top: 22rpx; padding: 32rpx; border-radius: 28rpx; background: #fff; }
.field-label { display: block; margin: 28rpx 0 14rpx; color: #596174; font-size: 25rpx; }
.field-label:first-child { margin-top: 0; }
.amount-input, .text-input { box-sizing: border-box; width: 100%; height: 88rpx; border-radius: 18rpx; background: #f6f7fa; }
.amount-input { display: flex; align-items: center; padding: 0 24rpx; }
.amount-input text { font-size: 38rpx; font-weight: 650; }
.amount-input input { flex: 1; margin-left: 16rpx; font-size: 38rpx; }
.text-input { padding: 0 24rpx; font-size: 27rpx; }
.method-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12rpx; }
.method-grid view { padding: 22rpx 8rpx; border: 2rpx solid transparent; border-radius: 18rpx; text-align: center; background: #f5f7fb; color: #6e7687; font-size: 23rpx; }
.method-grid .selected { border-color: #6290ed; color: #3f70d7; background: #edf3ff; }
.upload-box { height: 280rpx; border: 2rpx dashed #cbd3e2; border-radius: 20rpx; display: flex; align-items: center; justify-content: center; color: #778195; background: #fafbfc; font-size: 25rpx; overflow: hidden; }
.upload-box image { width: 100%; height: 100%; }
.privacy-row { margin-top: 34rpx; display: flex; align-items: flex-start; color: #7f8797; font-size: 22rpx; line-height: 1.55; }
.check { width: 34rpx; height: 34rpx; margin: 2rpx 16rpx 0 0; flex: none; border: 2rpx solid #b8c0ce; border-radius: 8rpx; color: #fff; text-align: center; line-height: 34rpx; }
.checked { border-color: #4f84ea; background: #4f84ea; }
.submit-btn { height: 94rpx; margin-top: 28rpx; border-radius: 47rpx; display: flex; align-items: center; justify-content: center; color: #fff; background: linear-gradient(135deg, #4f84ea, #8178ef); font-size: 29rpx; font-weight: 650; }
.disabled { opacity: .55; }
.confirm-card { margin-top: 0; }
.confirm-title, .confirm-amount { display: block; text-align: center; }
.confirm-title { font-size: 30rpx; font-weight: 650; }
.confirm-amount { margin: 24rpx 0 32rpx; font-size: 56rpx; color: #3f70d7; font-weight: 700; }
.confirm-row { display: flex; justify-content: space-between; padding: 22rpx 0; border-top: 2rpx solid #f0f2f5; color: #777f90; font-size: 25rpx; }
.confirm-row text:last-child { max-width: 65%; color: #263044; text-align: right; }
.confirm-notice { margin-top: 28rpx; padding: 22rpx; border-radius: 18rpx; color: #8a641f; background: #fff6df; font-size: 22rpx; line-height: 1.55; }
.record-card { margin-bottom: 20rpx; padding: 28rpx; border-radius: 25rpx; background: #fff; }
.record-head, .record-main, .record-foot { display: flex; justify-content: space-between; align-items: center; }
.record-head { color: #737c8d; font-size: 21rpx; }
.record-head text:last-child { padding: 7rpx 14rpx; border-radius: 16rpx; }
.pending { color: #9a6a18; background: #fff3d9; }
.success { color: #25845a; background: #e8f7ef; }
.danger { color: #c65151; background: #ffeded; }
.record-main { margin-top: 24rpx; }
.record-main text:first-child { font-size: 38rpx; font-weight: 700; }
.record-main text:last-child { color: #697285; font-size: 24rpx; }
.record-foot { margin-top: 24rpx; padding-top: 20rpx; border-top: 2rpx solid #f0f2f5; color: #9aa1af; font-size: 21rpx; }
.empty, .footer { padding: 90rpx 0; text-align: center; color: #9aa1af; font-size: 24rpx; }
.footer { padding: 28rpx 0; }
</style>
