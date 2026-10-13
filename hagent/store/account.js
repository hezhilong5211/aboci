/**
 * 账户状态管理
 * 管理豆子余额、会员信息、消费明细
 * 数据来源:批4 接入 api/account 后填充(当前 actions 为占位)
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAccountStore = defineStore('account', () => {
	// ====== 状态 ======

	/** 豆子余额 */
	const balance = ref(0)
	/** 会员等级 */
	const memberLevel = ref(null)
	/** 会员到期时间 */
	const memberExpire = ref(null)
	/** 消费/充值明细列表 */
	const transactions = ref([])

	// ====== 方法 ======

	/**
	 * 设置余额
	 * @param {number} n
	 */
	function setBalance(n) {
		balance.value = n
	}

	/**
	 * 设置会员信息
	 * @param {string|null} level - 会员等级
	 * @param {string|null} expire - 到期时间
	 */
	function setMember(level, expire) {
		memberLevel.value = level
		memberExpire.value = expire
	}

	/**
	 * 设置明细列表
	 * @param {Array} list
	 */
	function setTransactions(list) {
		transactions.value = list
	}

	return {
		balance,
		memberLevel,
		memberExpire,
		transactions,
		setBalance,
		setMember,
		setTransactions
	}
})
