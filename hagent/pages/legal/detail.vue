<template>
	<view class="legal-page" :class="pagePerformanceClass">
		<view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
			<view class="nav-inner">
				<view class="nav-back" @tap="goBack">
					<text class="back-icon">‹</text>
				</view>
				<text class="nav-title">{{ document.title }}</text>
				<view class="nav-placeholder" />
			</view>
		</view>

		<scroll-view class="legal-scroll" scroll-y :enhanced="scrollEnhanced" :bounces="scrollBounces" :show-scrollbar="scrollShowScrollbar" :lower-threshold="scrollLowerThreshold">
			<view class="legal-inner" :style="[contentStyle, { paddingTop: (statusBarHeight + 52) + 'px' }]">
				<view class="hero-card">
					<view class="hero-icon">
						<text class="hero-icon-text">{{ document.type === 'privacy' ? '▣' : '▤' }}</text>
					</view>
					<text class="hero-title">{{ document.title }}</text>
					<text class="hero-date">生效日期：{{ document.effectiveDate }}</text>
					<text class="hero-date">更新日期：{{ document.updatedDate }}</text>
					<text class="hero-desc">{{ document.subtitle }}</text>
				</view>

				<view class="catalog-card">
					<text class="card-title">目录</text>
					<view class="catalog-item" v-for="(section, index) in document.sections" :key="section.title" @tap="showToast(section.title)">
						<text class="catalog-text">{{ section.title }}</text>
						<text class="catalog-arrow">›</text>
					</view>
				</view>

				<view class="section-card" v-for="section in document.sections" :key="section.title">
					<text class="section-title">{{ section.title }}</text>
					<text class="section-paragraph" v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</text>
					<view class="legal-item" v-for="item in section.items" :key="item.title">
						<view class="legal-item-icon">
							<text class="legal-item-icon-text">{{ item.icon || '□' }}</text>
						</view>
						<view class="legal-item-main">
							<text class="legal-item-title">{{ item.title }}</text>
							<text class="legal-item-desc">{{ item.desc }}</text>
						</view>
					</view>
					<view class="notice-box" v-if="section.notice">
						<text class="notice-title">提示</text>
						<text class="notice-text">{{ section.notice }}</text>
					</view>
				</view>

				<view class="contact-card" v-if="document.contactEmail">
					<text class="card-title">联系我们</text>
					<view class="contact-row">
						<text class="contact-icon">✉</text>
						<text class="contact-email">{{ document.contactEmail }}</text>
						<text class="catalog-arrow">›</text>
					</view>
				</view>
			</view>
		</scroll-view>

	</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { usePageScroll } from '@/composables/usePageScroll.js'
import { usePerformanceMode } from '@/composables/usePerformanceMode.js'

const { pagePerformanceClass } = usePerformanceMode()
const { scrollEnhanced, scrollBounces, scrollShowScrollbar, scrollLowerThreshold, contentStyle } = usePageScroll({ extraBottom: '34rpx' })
const statusBarHeight = ref(44)
const document = ref(createFallbackDocument('privacy'))

uni.getSystemInfo({
	success: (info) => {
		statusBarHeight.value = info.statusBarHeight || 44
	},
})

onLoad((query) => {
	const type = normalizeType(query?.type || 'privacy')
	// 协议内容走前端兜底文案,不调后端:后端无 /api/mobile/legal 端点,
	// 且游客无 token 调用会触发 401 全局登出(表现为闪退回登录页)。
	document.value = createFallbackDocument(type)
})

function normalizeType(type) {
	if (type === 'recharge' || type === 'purchase') return 'recharge'
	if (type === 'distribution') return 'distribution'
	return ['terms', 'user', 'agreement', 'service'].includes(type) ? 'terms' : 'privacy'
}

function createFallbackDocument(type) {
	// 后端协议接口返回前的本地兜底文案；type 决定服务协议 / 隐私政策
	if (type === 'recharge') return createRechargeDocument()
	if (type === 'distribution') return createDistributionDocument()
	return type === 'terms' ? createTermsDocument() : createPrivacyDocument()
}

// 分销商使用推广中心前需知悉的归因、佣金、退款和提现规则。
function createDistributionDocument() {
	return {
		type: 'distribution',
		title: '推广规则',
		subtitle: '本规则说明一级推广的绑定、佣金、退款冲正和提现处理方式。',
		effectiveDate: '2026-07-21',
		updatedDate: '2026-07-26',
		contactEmail: 'support@abopi.example.com',
		sections: [
			{
				title: '一、推广资格与绑定',
				paragraphs: [
					'推广资格由平台后台开通，小程序不提供自助申请入口。',
					'用户首次通过有效推广二维码进入并完成登录后，与对应分销商永久绑定，之后扫描其他二维码不会覆盖。',
					'新用户和已注册用户都可首次绑定，绑定前的历史订单不追溯且不补发佣金。',
				],
			},
			{
				title: '二、佣金计算与到账',
				paragraphs: [
					'绑定后用户实付的会员订单和加油包订单参与佣金，佣金比例以订单支付时平台的统一配置为准。',
					'佣金按订单实付金额计算，不足一分的部分向下取整，订单支付且权益履约成功后立即进入可提现余额，不设冻结期。',
					'推广资格停用期间不产生新佣金，已有历史数据和可提现余额仍保留。',
				],
			},
			{
				title: '三、退款冲正',
				paragraphs: [
					'参与佣金的订单发生退款时，系统会按原订单佣金自动冲正。',
					'佣金已被待审核提现锁定时，相关提现会标记为需复核并暂停打款。',
					'佣金已经提现后发生退款的，将形成待抵扣佣金，后续新佣金优先用于抵扣。',
				],
			},
			{
				title: '四、提现与收款资料',
				paragraphs: [
					'单笔提现最低金额以页面展示的平台统一配置为准，单笔最高金额为提交时的当前可提现余额；每日提现次数不限，但申请总额不得超过当前可提现余额。',
					'提现申请全天开放，每天 00:00–24:00 均可提交。',
					'申请提交后，对应金额将被锁定；申请被驳回后，符合规则的锁定金额将返还至可提现余额。',
					'平台预计在提交后的 1–3 个工作日内完成人工审核和线下打款；周末及法定节假日顺延，实际到账时间以收款渠道为准。',
					'确认打款时，付款凭证和付款流水号均为选填；平台对已填写或上传的资料进行私有留档。',
					'提现可选择微信收款码、支付宝收款码或银行卡。',
					'收款人姓名、银行卡号、收款码仅用于本次审核、打款和必要的财务留档，完整资料仅限经授权管理员查看。',
				],
				notice: '本期不实现自动转账、税额计算或自动报税，线下财税处理以平台后续通知及法律法规为准。',
			},
		],
	}
}

// 服务协议兜底文案（平台：啊播吡，面向美业的 AI 创作小程序）
function createTermsDocument() {
	return {
		type: 'terms',
		title: '服务协议',
		subtitle: '您开通会员和充值勾选同意后，说明您同意，本协议说明的啊播吡 AI小程序权益，充值规则与计费规则。',
		effectiveDate: '2026-07-20',
		updatedDate: '2026-07-20',
		// TODO: 上线前替换为正式客服邮箱
		contactEmail: 'support@abopi.example.com',
		sections: [
			{
				title: '一、服务说明',
				paragraphs: [
					'啊播吡是一款面向美业从业者的 AI 创作小程序，提供对话式 AI 助手、文生图与图生图、文生视频以及行业案例模板等功能。',
					'你可以通过对话描述需求，由 AI 协助生成文案、图片与视频，并基于平台提供的案例模板快速产出内容。',
					'本平台提供的是创作辅助工具，最终内容的选用、发布与商用均由你自行决定并负责。',
				],
			},
			{
				title: '二、账号与登录',
				paragraphs: [
					'你需通过微信授权登录使用本服务，部分功能可能要求你绑定手机号以完成账户验证。',
					'你应妥善保管账号及登录凭证，因你自身保管不善导致的账号被盗用或操作损失，由你自行承担。',
					'如发现账号存在异常或被他人冒用，请及时联系我们处理。',
				],
			},
			{
				title: '三、豆子虚拟代币',
				paragraphs: [
					'豆子是平台内的虚拟代币，仅用于抵扣 AI 对话、出图、出视频等消费，不可提现、不可转让、不等同于法定货币。',
					'会员赠送的豆子随对应的连续会员权益最终到期而失效；系统扣豆时优先使用有期限的会员赠豆。',
					'有效会员购买的加油包豆永久有效，不受后续会员到期影响，会员到期后仍可继续使用。',
				],
				notice: '会员与加油包属于即时发放的虚拟商品，成功履约后不支持用户主动退款；法律法规、平台规则另有要求或权益未成功发放的除外。',
			},
			{
				title: '四、会员订阅',
				paragraphs: [
					'每种会员的有效天数以下单页面实时展示为准，自本次会员段开始时间起计算；连续购买时按会员段依次顺延。',
					'会员到期后会员身份权益终止，尚未消费的会员赠豆失效，但已购买的加油包豆继续保留。',
					'具体的会员价格、权益与赠送规则以下单页面实时展示为准。',
				],
			},
			{
				title: '五、AI 生成内容',
				paragraphs: [
					'你对输入给 AI 的文字、图片等内容负责，须确保拥有相应权利且不侵犯他人合法权益。',
					'AI 生成结果由模型自动产出，平台不保证其准确、完整或适用于任何特定用途，使用前请自行核实。',
					'在符合法律法规及本协议的前提下，你可使用自己生成的内容；但不得将生成内容用于违法、侵权或其他不正当用途。',
				],
			},
			{
				title: '六、用户行为规范',
				paragraphs: [
					'你不得利用本服务从事任何违法违规活动，包括但不限于制作或传播涉黄、涉政、暴力、欺诈及侵权内容。',
					'你不得上传或生成侵犯他人知识产权、肖像权、隐私权等合法权益的内容。',
					'对于违反规范的行为，平台有权采取限制功能、删除内容直至封禁账号等措施。',
				],
			},
			{
				title: '七、免责声明',
				paragraphs: [
					'因不可抗力、网络故障、第三方服务中断等非平台原因导致的服务暂停或数据异常，平台在法律允许范围内不承担责任。',
					'你因使用 AI 生成内容产生的任何后果，由你自行承担，平台不对内容的合法性与适用性作出保证。',
					'平台会尽合理努力保障服务稳定，但不保证服务绝对不中断或无差错。',
				],
			},
			{
				title: '八、协议变更与终止',
				paragraphs: [
					'平台可根据法律法规或业务调整对本协议进行更新，对充值和计费规则适当调整，均以页面实时展示为准，更新后将在应用内以适当方式提示。',
					'协议更新后你继续使用本服务，即视为接受变更后的条款；如不同意，应停止使用。',
					'你可随时停止使用本服务，平台亦有权依据本协议对违规账号终止提供服务。',
				],
			},
		],
	}
}

// 充值与会员购买协议：购买页和加油包页共同使用。
function createRechargeDocument() {
	return {
		type: 'recharge',
		title: '充值与会员购买协议',
		subtitle: '本协议说明会员、会员赠豆和加油包的购买、有效期、使用及退款规则。',
		effectiveDate: '2026-07-20',
		updatedDate: '2026-07-20',
		contactEmail: '13051812086@163.com',
		sections: [
			{
				title: '一、商品性质',
				paragraphs: [
					'会员权益、会员赠豆和加油包豆均为平台内即时发放的虚拟商品，仅限购买账号本人使用。',
					'上述虚拟商品不可提现、不可转让，不具备法定货币属性。',
				],
			},
			{
				title: '二、会员有效期',
				paragraphs: [
					'每次购买增加的会员有效天数以下单页面实时展示为准；已有有效会员时，新会员段在现有会员段结束后顺延生效。',
					'会员赠豆在会员权益生效或履约时到账，尚未消费的会员赠豆随连续会员权益最终到期而失效。',
				],
			},
			{
				title: '三、加油包规则',
				paragraphs: [
					'只有有效会员可以购买加油包。购买时会员资格失效的，订单不会发放加油包权益。',
					'加油包豆到账后永久有效，不随会员到期清零；会员到期后仍可继续使用剩余加油包豆。',
					'系统消费豆子时优先扣除有期限的会员赠豆，再扣除永久有效的加油包豆。',
				],
			},
			{
				title: '四、支付与到账',
				paragraphs: [
					'商品价格、赠豆数量及会员等级以下单页面展示为准。支付完成后，系统将在确认支付结果后发放对应权益。',
					'若支付成功但权益未到账，请勿重复支付，并通过购买记录及客服渠道核查。',
				],
			},
			{
				title: '五、不退款规则与例外',
				paragraphs: [
					'会员或加油包成功履约后即视为虚拟商品交付完成，不支持因个人原因发起退款，请在支付前确认商品内容。',
					'因系统故障导致支付成功但权益未发放、重复扣款，或法律法规及微信平台规则要求退款的，平台将按核验结果处理。',
					'异常退款发生时，平台有权撤销该退款订单对应且尚未消费的权益，但不会清除与该订单无关的永久加油包豆。',
				],
				notice: '点击支付即表示你已阅读并同意本协议及用户协议。',
			},
		],
	}
}

// 隐私政策兜底文案（平台：啊播吡，面向美业的 AI 创作小程序）
function createPrivacyDocument() {
	return {
		type: 'privacy',
		title: '隐私政策',
		subtitle: '本政策说明啊播吡 AI 小程序如何处理和保护你的个人信息。',
		effectiveDate: '2026-06-25',
		updatedDate: '2026-08-06',
		contactEmail: '13051812086@163.com',
		sections: [
			{
				title: '一、适用范围与运营者',
				paragraphs: [
					'本政策适用于啊播吡 AI 小程序，运营者为微信公众平台展示的本小程序开发者主体（以下简称“我们”）。',
					'我们仅在实现产品功能所必要的范围内处理你的个人信息。你可以先浏览公开内容；使用登录、AI 创作、购买、推广或提现功能时，我们会根据对应功能处理必要信息。',
				],
			},
			{
				title: '二、我们处理的信息',
				paragraphs: [
					'微信账号与登录信息：你使用微信登录时，我们通过微信登录凭证获取 openid、unionid 并保存必要的会话状态，用于识别账号、维持登录状态和完成微信虚拟支付。',
					'手机号：仅在你点击手机号快捷登录或主动绑定并授权后获取，用于账号注册、绑定、登录验证和账户识别。',
					'个人资料：你主动设置的昵称和头像，用于个人中心展示、账号识别，以及在分销推广关系中展示必要的用户资料。',
					'AI 创作信息：你输入的对话文字、澄清答案、个性化或收藏提示词、主动上传的参考图片、生成参数、AI 回复及生成作品，用于提供对话、识图和图片生成服务。',
					'交易与权益信息：会员或豆子加油包的订单号、商品、金额、支付渠道、支付及退款状态、会员记录、豆子余额和变动流水，用于支付核对、权益发放、售后和账务处理。',
					'推广信息：推广码访问、微信启动场景、绑定关系、绑定时间、推广订单汇总、佣金及提现流水，用于执行一级推广、佣金结算和退款冲正规则。',
					'提现资料：分销商主动提交的收款人姓名、银行名称、银行卡号，或微信、支付宝收款码，以及提现金额、审核记录、打款流水和付款凭证，用于提现审核、线下打款和必要的财务留档。',
				],
			},
			{
				title: '三、设备功能与网络信息',
				paragraphs: [
					'当你主动选择或拍摄头像、AI 参考图片或提现收款码时，小程序会读取你本次选择或拍摄的图片并上传至服务器处理，不会自行读取未选择的其他照片。',
					'当你主动保存 AI 生成作品或推广二维码时，我们会申请相册（仅写入）权限，只将你选择保存的图片写入相册，不读取相册中的其他内容。',
					'当你主动点击复制时，小程序只会把你选择的文案、提示词或订单号写入剪切板，不会读取剪切板中原有的内容。',
					'小程序会在本地处理必要的设备系统、屏幕、安全区及网络连接状态，用于界面和性能适配、判断网络是否可用；当前不会将设备信息用于广告画像。',
					'服务器会处理 IP 地址、访问时间、请求地址、响应状态及必要的错误日志，用于接口限流、防攻击、故障排查和保障服务安全。',
				],
			},
			{
				title: '四、信息使用目的',
				paragraphs: [
					'我们使用上述信息提供账号登录、个人资料、AI 创作、作品管理、会员与豆子、支付、推广及提现服务。',
					'我们会使用订单、豆子、会员、佣金和提现记录完成计费、权益履约、财务核对、退款处理及争议解决。',
					'我们可能对汇总或去标识化后的数据进行服务质量分析，但不会据此直接识别特定个人。',
				],
			},
			{
				title: '五、共享与委托处理',
				paragraphs: [
					'微信服务：登录、手机号授权、微信支付、虚拟支付及文本内容安全能力由腾讯微信提供。进行文本安全检查时，我们会向微信提供必要的账号标识和本次输入文字。',
					'AI 模型服务：截至本政策更新日期，正式环境启用 OpenLux AI 接口服务（api.openlux.ai）、深度求索 DeepSeek（api.deepseek.com）及火山引擎方舟（ark.cn-beijing.volces.com）。我们会根据对话、知识检索或图片生成任务，向对应服务传输完成任务所必要的文字、对话上下文、提示词、参考图片和生成参数。',
					'如 AI 模型服务商、处理目的或信息类型发生实质变化，我们会及时更新本政策，并在法律要求时重新取得你的同意。',
					'分销披露：用户绑定推广关系后，对应上级分销商可以查看该用户的昵称、头像、脱敏手机号、绑定时间，以及订单数量、订单金额和佣金汇总；不会向其展示完整手机号或完整支付资料。',
					'提现资料仅向资料所属分销商及具有相应敏感信息权限的管理员开放，用于提现审核和打款。除实现上述功能、取得你的单独同意或法律法规另有规定外，我们不会出售或向无关主体提供你的个人信息。',
				],
			},
			{
				title: '六、存储、安全与保留期限',
				paragraphs: [
					'账号标识、手机号、昵称和头像通常保存至账号注销或我们不再提供服务；头像、参考图片及生成作品保存在业务服务器中。',
					'对话历史与生成作品默认保留 30 天，到期后由系统分批清理，请及时保存需要的内容。',
					'订单、会员、豆子、推广、佣金和提现记录，会在完成交易、履约、售后、争议处理及法律法规要求的必要期限内保存。',
					'银行卡号采用加密存储并提供脱敏展示；收款码及付款凭证使用非公开私有存储。手机号通过传输保护、后台访问控制和展示脱敏等措施保护。',
					'我们对后台管理和敏感资料采取权限控制、传输保护、操作审计和数据备份等合理安全措施。达到保存期限后，将依法删除或匿名化处理，但法律法规要求继续保留的除外。',
				],
				notice: 'AI 对话和生成作品默认保留 30 天；交易、财务及安全记录按业务和法定期限保留。',
			},
			{
				title: '七、内容安全',
				paragraphs: [
					'为维护平台安全，我们会通过微信内容安全能力检查你提交的对话文字。',
					'检查通过的文字不会另行写入审核记录；检测到风险时，系统可能拒绝本次请求，并保留不超过 500 个字符的必要审核摘要和处置结果。',
					'当前不表示所有上传图片和 AI 生成图片均已完成自动安全审核；你仍需确保上传和使用的内容合法并已取得必要权利。',
				],
			},
			{
				title: '八、你的权利',
				paragraphs: [
					'你可以在个人设置中查看或修改昵称和头像，并可在微信或手机系统设置中关闭相册等权限；关闭后仅影响依赖该权限的功能。',
					'如需查询、复制、更正或删除其他个人信息，撤回同意，或申请注销账号及删除关联数据，请发送邮件至 13051812086@163.com。当前小程序暂未提供自助注销入口。',
					'收到请求后，我们会核实身份并在合理期限内处理。订单、财务、安全及争议记录依法需要保留的，在相应期限届满前可能无法立即删除。',
				],
			},
			{
				title: '九、未成年人、政策更新与联系',
				paragraphs: [
					'本服务主要面向成年从业者，不主动面向未成年人提供服务。未成年人应在监护人指导和同意下使用。',
					'本政策发生重要变化时，我们会通过小程序页面或其他适当方式提示；涉及新增敏感信息处理目的时，将依法重新取得你的同意。',
					'如对本政策或个人信息处理有疑问、意见或投诉，请通过本政策所列邮箱联系我们。',
				],
			},
		],
	}
}

function goBack() {
	uni.navigateBack()
}

function showToast(title) {
	uni.showToast({ title, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.legal-page {
	height: 100vh;
	background: #f5f7fb;
	position: relative;
	overflow: hidden;
}

.nav-bar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 30;
	background: rgba(255, 255, 255, 0.96);
}

.nav-inner {
	height: 104rpx;
	padding: 0 24rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	box-sizing: border-box;
}

.nav-back,
.nav-placeholder {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.back-icon {
	font-size: 54rpx;
	color: var(--am-color-text-title);
	line-height: 1;
}

.nav-title {
	max-width: 460rpx;
	font-size: 30rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 40rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.legal-scroll {
	height: 100%;
}

.legal-inner {
	padding-left: 28rpx;
	padding-right: 28rpx;
	box-sizing: border-box;
}

.hero-card,
.catalog-card,
.section-card,
.contact-card {
	margin-bottom: 22rpx;
	padding: 28rpx;
	border-radius: 28rpx;
	background: #ffffff;
	box-shadow: var(--am-shadow-card);
	box-sizing: border-box;
}

.hero-card {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
}

.hero-icon {
	width: 96rpx;
	height: 96rpx;
	border-radius: 28rpx;
	background: linear-gradient(135deg, #dceaff 0%, #7fb0ff 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 20rpx;
}

.hero-icon-text {
	font-size: 44rpx;
	color: #ffffff;
	line-height: 1;
}

.hero-title {
	font-size: 36rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 48rpx;
}

.hero-date,
.hero-desc {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: var(--am-color-text-secondary);
	line-height: 36rpx;
}

.hero-desc {
	margin-top: 22rpx;
	color: var(--am-color-text-body);
}

.card-title,
.section-title {
	display: block;
	font-size: 28rpx;
	color: var(--am-color-text-title);
	font-weight: 800;
	line-height: 38rpx;
}

.catalog-item,
.contact-row {
	min-height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: space-between;
	border-top: 1rpx solid var(--am-color-border-light);
}

.catalog-item:first-of-type {
	margin-top: 16rpx;
}

.catalog-text,
.contact-email {
	flex: 1;
	min-width: 0;
	font-size: 24rpx;
	color: var(--am-color-text-body);
	line-height: 34rpx;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.catalog-arrow {
	font-size: 36rpx;
	color: var(--am-color-text-tertiary);
	line-height: 1;
	flex-shrink: 0;
}

.section-paragraph {
	display: block;
	margin-top: 18rpx;
	font-size: 26rpx;
	color: var(--am-color-text-body);
	line-height: 44rpx;
}

.legal-item {
	margin-top: 18rpx;
	padding: 20rpx;
	border-radius: 20rpx;
	background: #fbfcff;
	display: flex;
	gap: 18rpx;
}

.legal-item-icon {
	width: 42rpx;
	height: 42rpx;
	border-radius: 12rpx;
	background: #eaf2ff;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.legal-item-icon-text {
	font-size: 24rpx;
	color: var(--am-color-primary);
	line-height: 1;
}

.legal-item-main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.legal-item-title {
	font-size: 25rpx;
	color: var(--am-color-text-title);
	font-weight: 700;
	line-height: 34rpx;
}

.legal-item-desc {
	margin-top: 4rpx;
	font-size: 23rpx;
	color: var(--am-color-text-secondary);
	line-height: 34rpx;
}

.notice-box {
	margin-top: 22rpx;
	padding: 20rpx;
	border-radius: 18rpx;
	background: #eef5ff;
}

.notice-title,
.notice-text {
	display: block;
	font-size: 23rpx;
	line-height: 34rpx;
}

.notice-title {
	color: var(--am-color-primary);
	font-weight: 800;
}

.notice-text {
	margin-top: 4rpx;
	color: var(--am-color-text-secondary);
}

.contact-icon {
	margin-right: 12rpx;
	font-size: 26rpx;
	color: var(--am-color-primary);
	line-height: 1;
}

.am-performance-light .hero-card,
.am-performance-light .catalog-card,
.am-performance-light .section-card,
.am-performance-light .contact-card {
	box-shadow: none;
}
</style>
