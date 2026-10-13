<template>
  <div class="app-container">
    <a-card class="table-card" title="会员等级">
      <template #extra>
        <a-button type="primary" @click="handleAdd" v-hasPermi="['membership:level:add']">
          <template #icon><icon-plus /></template>
          新增等级
        </a-button>
      </template>

      <a-table
        :loading="loading"
        :data="levelList"
        :pagination="false"
        row-key="levelId"
      >
        <template #columns>
          <a-table-column title="序号" data-index="sort" align="center" :width="100" />
          <a-table-column title="名称" data-index="name" align="center" ellipsis tooltip />
          <a-table-column title="送豆数" data-index="grantBeans" align="center" :width="120" />
          <a-table-column title="价格(元)" data-index="price" align="center" :width="120" />
          <a-table-column title="微信道具 ID" data-index="virtualProductId" align="center" :width="180" ellipsis tooltip>
            <template #cell="{ record }">{{ record.virtualProductId || '-' }}</template>
          </a-table-column>
          <a-table-column title="会员时长" align="center" :width="120">
            <template #cell="{ record }">{{ record.durationDays }} 天</template>
          </a-table-column>
          <a-table-column title="状态" align="center" :width="100">
            <template #cell="{ record }">
              <a-tag :color="record.status === '0' ? 'green' : 'gray'">
                {{ record.status === '0' ? '上架' : '下架' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" align="center" :width="160">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="handleEdit(record)" v-hasPermi="['membership:level:edit']">编辑</a-button>
                <a-button type="text" size="small" status="danger" @click="handleRemove(record)" v-hasPermi="['membership:level:remove']">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:visible="formOpen"
      :title="formTitle"
      :width="620"
      @before-ok="submitForm"
      @cancel="formOpen = false"
    >
      <a-form ref="formRef" :model="form" :rules="formRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="名称" field="name">
          <a-input v-model="form.name" placeholder="请输入等级名称" :max-length="64" allow-clear />
        </a-form-item>
        <a-form-item label="支付方式">
          <a-input model-value="微信虚拟支付·普通道具" readonly />
        </a-form-item>
        <a-form-item label="微信道具 ID" field="virtualProductId">
          <a-input
            v-model="form.virtualProductId"
            placeholder="请输入公众平台道具 ID"
            :max-length="20"
            allow-clear
          />
          <template #extra>仅支持英文字母、数字和下划线，最长 20 位</template>
        </a-form-item>
        <a-form-item label="送豆数" field="grantBeans">
          <a-input-number v-model="form.grantBeans" placeholder="每周期赠送豆数" :min="0" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="价格(元)" field="price">
          <a-input-number
            v-model="form.price"
            placeholder="请输入价格"
            :min="0.01"
            :max="10000"
            :precision="2"
            :step="0.01"
            style="width: 100%;"
          />
          <template #extra>价格必须与小程序公众平台道具价格完全一致</template>
        </a-form-item>
        <a-form-item label="会员时长(天)" field="durationDays">
          <a-input-number
            v-model="form.durationDays"
            placeholder="请输入会员有效天数"
            :min="1"
            :max="3650"
            :precision="0"
            :step="1"
            style="width: 100%;"
          />
          <template #extra>该时长仅用于后续新订单和后台新开通会员，历史权益不变</template>
        </a-form-item>
        <a-form-item label="序号" field="sort">
          <a-input-number v-model="form.sort" placeholder="数值越小越靠前" :min="0" :step="1" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="状态" field="status">
          <a-switch v-model="form.status" checked-value="0" unchecked-value="1">
            <template #checked>上架</template>
            <template #unchecked>下架</template>
          </a-switch>
        </a-form-item>
        <a-form-item label="卡片背景图" field="cardBg">
          <div>
            <a-upload
              :show-file-list="false"
              :auto-upload="true"
              :custom-request="handleCardBgUpload"
              accept="image/*"
            >
              <template #upload-button>
                <div class="cardbg-uploader">
                  <img v-if="form.cardBg" :src="fullUrl(form.cardBg)" class="cardbg-preview" alt="卡片背景图" />
                  <div v-else class="cardbg-placeholder">
                    <icon-plus />
                    <span>上传</span>
                  </div>
                </div>
              </template>
            </a-upload>
            <div class="cardbg-tip">建议尺寸 690×320px(约 2.16:1),用于个人中心会员卡背景</div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconPlus } from '@arco-design/web-vue/es/icon';
import {
  listMembershipLevel,
  addMembershipLevel,
  updateMembershipLevel,
  removeMembershipLevel,
  MembershipLevel
} from '@/api/membership';
import { uploadAvatar } from '@/api/system';

// 后端返回相对路径(如 /profile/...)时拼接 /api 前缀以可预览;已是完整 URL 则原样返回
function fullUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('/')) return '/api' + url;
  return url;
}

const loading = ref(false);
const levelList = ref<MembershipLevel[]>([]);
const DEFAULT_MEMBERSHIP_DURATION_DAYS = 365;

function getLevelList() {
  loading.value = true;
  listMembershipLevel()
    .then((res: any) => {
      levelList.value = Array.isArray(res) ? res : res?.records || [];
    })
    .finally(() => {
      loading.value = false;
    });
}

// ---- 新增/编辑弹窗 ----
const formOpen = ref(false);
const formRef = ref();
const formTitle = ref('新增等级');
// 是否编辑态:有 levelId 即编辑
const form = reactive<MembershipLevel>({
  levelId: undefined,
  name: '',
  grantBeans: 0,
  price: undefined,
  virtualProductId: '',
  durationDays: DEFAULT_MEMBERSHIP_DURATION_DAYS,
  sort: 0,
  cardBg: '',
  status: '0'
});
const formRules = {
  name: [{ required: true, message: '请输入等级名称' }],
  virtualProductId: [
    {
      validator: (value: string | undefined, callback: (error?: string) => void) => {
        if (form.status === '0' && !value) {
          callback('上架会员必须填写微信道具 ID');
          return;
        }
        if (value && !/^[A-Za-z0-9_]{1,20}$/.test(value)) {
          callback('道具 ID 仅支持英文字母、数字和下划线，最长 20 位');
          return;
        }
        callback();
      }
    }
  ],
  grantBeans: [{ required: true, message: '请输入送豆数' }],
  price: [
    { required: true, message: '请输入价格' },
    {
      validator: (value: number | undefined, callback: (error?: string) => void) => {
        if (value != null && (value < 0.01 || value > 10000)) {
          callback('价格必须在 0.01 至 10000.00 元之间');
          return;
        }
        if (value != null && Math.abs(value * 100 - Math.round(value * 100)) > 1e-8) {
          callback('价格最多保留两位小数');
          return;
        }
        callback();
      }
    }
  ],
  durationDays: [
    { required: true, message: '请输入会员时长' },
    {
      validator: (value: number | undefined, callback: (error?: string) => void) => {
        if (value != null && (!Number.isInteger(value) || value < 1 || value > 3650)) {
          callback('会员时长必须是 1 至 3650 之间的整数');
          return;
        }
        callback();
      }
    }
  ],
  sort: [{ required: true, message: '请输入序号' }]
};

function resetForm() {
  form.levelId = undefined;
  form.name = '';
  form.grantBeans = 0;
  form.price = undefined;
  form.virtualProductId = '';
  form.durationDays = DEFAULT_MEMBERSHIP_DURATION_DAYS;
  form.sort = 0;
  form.cardBg = '';
  form.status = '0';
}

function handleAdd() {
  resetForm();
  formTitle.value = '新增等级';
  formOpen.value = true;
}

function handleEdit(record: MembershipLevel) {
  form.levelId = record.levelId;
  form.name = record.name;
  form.grantBeans = record.grantBeans;
  form.price = record.price;
  form.virtualProductId = record.virtualProductId ?? '';
  form.durationDays = record.durationDays ?? DEFAULT_MEMBERSHIP_DURATION_DAYS;
  form.sort = record.sort;
  form.cardBg = record.cardBg ?? '';
  form.status = record.status;
  formTitle.value = '编辑等级';
  formOpen.value = true;
}

// 卡片背景图单图上传
async function handleCardBgUpload(option: any) {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    form.cardBg = fileUrl as unknown as string;
    Message.success('背景图上传成功');
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '背景图上传失败');
    option.onError(error);
  }
}

function submitForm(done: (closed: boolean) => void) {
  formRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const payload: MembershipLevel = { ...form };
    const req = form.levelId ? updateMembershipLevel(payload) : addMembershipLevel(payload);
    req.then(() => {
      Message.success(form.levelId ? '修改成功' : '新增成功');
      getLevelList();
      done(true);
    }).catch(() => done(false));
  });
}

function handleRemove(record: MembershipLevel) {
  Modal.confirm({
    title: '删除确认',
    content: `确认要删除等级 "${record.name}" 吗?已开通用户的权益不受影响。`,
    onOk: () => {
      removeMembershipLevel(record.levelId!).then(() => {
        Message.success('删除成功');
        getLevelList();
      });
    }
  });
}

getLevelList();
</script>

<style scoped>
.app-container {
  padding: 0px;
}

/* 卡片背景图上传区:按建议比例 2.16:1 预览 */
.cardbg-uploader {
  width: 216px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--color-neutral-3);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;
  background: var(--color-fill-1);
}

.cardbg-uploader:hover {
  border-color: rgb(var(--primary-6));
}

.cardbg-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.cardbg-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
  font-size: 12px;
}

.cardbg-tip {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}
</style>
