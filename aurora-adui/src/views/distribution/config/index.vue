<template>
  <div class="app-container config-page">
    <a-card title="分销设置" :bordered="false" class="config-card">
      <a-alert type="warning" class="config-alert">比例和最低提现金额修改后只影响新订单与新提现申请，不重算历史佣金。生产首次上线时总开关必须保持关闭。</a-alert>
      <a-spin :loading="loading" style="width: 100%;">
        <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
          <a-form-item label="分销功能" field="enabled">
            <a-switch v-model="form.enabled" :checked-value="1" :unchecked-value="0"><template #checked>已开启</template><template #unchecked>已关闭</template></a-switch>
            <template #extra>关闭时不产生新访问归因、绑定和佣金，小程序不显示推广入口。</template>
          </a-form-item>
          <a-form-item label="全局佣金比例" field="ratePercent">
            <a-input-number v-model="form.ratePercent" :min="0" :max="100" :precision="2" :step="0.1" style="width: 100%;"><template #suffix>%</template></a-input-number>
            <template #extra>按订单实付金额计算，结果保留两位小数，多余部分向下舍去；保存后仅影响新订单。</template>
          </a-form-item>
          <a-form-item label="最低提现金额" field="minWithdrawYuan">
            <a-input-number v-model="form.minWithdrawYuan" :min="0" :precision="2" :step="10" style="width: 100%;"><template #prefix>¥</template></a-input-number>
          </a-form-item>
          <a-descriptions v-if="snapshot.updateTime" :column="1" bordered size="small" class="audit-info"><a-descriptions-item label="最后修改人">{{ snapshot.updateBy || '-' }}</a-descriptions-item><a-descriptions-item label="最后修改时间">{{ snapshot.updateTime }}</a-descriptions-item></a-descriptions>
          <a-button type="primary" long :loading="saving" @click="confirmSave" v-hasPermi="['distribution:config:edit']">保存设置</a-button>
        </a-form>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { DistributionConfig, getDistributionConfig, updateDistributionConfig } from '@/api/distribution';

const loading = ref(false); const saving = ref(false); const formRef = ref();
const form = reactive({ enabled: 0, ratePercent: 0, minWithdrawYuan: 0 });
const snapshot = reactive<DistributionConfig>({ enabled: 0, commissionRateBps: 0, minWithdrawAmount: 0 });
const rules = {
  ratePercent: [{ required: true, message: '请输入佣金比例' }, { validator: (value: number, cb: (msg?: string) => void) => cb(form.enabled === 1 && value <= 0 ? '启用前必须设置有效佣金比例' : undefined) }],
  minWithdrawYuan: [{ required: true, message: '请输入最低提现金额' }, { validator: (value: number, cb: (msg?: string) => void) => cb(form.enabled === 1 && value <= 0 ? '启用前必须设置有效最低提现金额' : undefined) }]
};

async function load() { loading.value = true; try { const data = await getDistributionConfig(); Object.assign(snapshot, data); form.enabled = data.enabled; form.ratePercent = data.commissionRateBps / 100; form.minWithdrawYuan = data.minWithdrawAmount / 100; } finally { loading.value = false; } }

async function confirmSave() {
  const errors = await formRef.value.validate(); if (errors) return;
  const oldText = `${snapshot.enabled === 1 ? '开启' : '关闭'} / ${(snapshot.commissionRateBps / 100).toFixed(2)}% / ¥${(snapshot.minWithdrawAmount / 100).toFixed(2)}`;
  const newText = `${form.enabled === 1 ? '开启' : '关闭'} / ${form.ratePercent.toFixed(2)}% / ¥${form.minWithdrawYuan.toFixed(2)}`;
  Modal.confirm({ title: '确认修改分销设置', content: `旧值：${oldText}\n新值：${newText}\n修改只影响新订单和新提现申请。`, onOk: save });
}

async function save() { saving.value = true; try { await updateDistributionConfig({ enabled: form.enabled, commissionRateBps: Math.round(form.ratePercent * 100), minWithdrawAmount: Math.round(form.minWithdrawYuan * 100) }); Message.success('分销设置已保存'); await load(); } finally { saving.value = false; } }
onMounted(load);
</script>

<style scoped>
.config-page { display: flex; justify-content: center; }.config-card { width: min(680px,100%); }.config-alert { margin-bottom: 20px; }.audit-info { margin-bottom: 20px; }
</style>
