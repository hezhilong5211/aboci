<template>
  <div class="app-container">
    <a-card class="table-card" title="系统设置">
      <a-tabs default-active-key="basic">
        <!-- 第一个 tab:基础配置(正方形卡片网格,一排 5 个,后续功能卡直接往里加) -->
        <a-tab-pane key="basic" title="基础配置">
          <div class="config-grid">
            <!-- 卡片:微信小程序配置 -->
            <a-card
              v-if="canReadWxConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canChangeWxConfig(miniApp) }"
              :hoverable="canChangeWxConfig(miniApp)"
              @click="canChangeWxConfig(miniApp) && handleEditMiniApp()"
            >
              <div class="config-card-head">
                <span class="config-card-title">微信小程序配置</span>
                <a-tag :color="miniApp.appId ? 'green' : 'gray'">
                  {{ miniApp.appId ? '已配置' : '未配置' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div v-if="miniApp.appId" class="config-line">AppId：{{ miniApp.appId }}</div>
                <div v-if="miniApp.appId" class="config-line">
                  状态：<a-tag size="small" :color="miniApp.isActive === 1 ? 'green' : 'gray'">
                    {{ miniApp.isActive === 1 ? '启用' : '停用' }}
                  </a-tag>
                </div>
                <div v-else class="config-empty">点击配置小程序 AppId / AppSecret</div>
              </div>
              <div class="config-card-foot">
                <template v-if="canChangeWxConfig(miniApp)"><icon-edit /> 点击编辑</template>
                <span v-else>仅查看</span>
              </div>
            </a-card>

            <!-- 卡片:微信支付配置 -->
            <a-card
              v-if="canReadWxConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canChangeWxConfig(pay) }"
              :hoverable="canChangeWxConfig(pay)"
              @click="canChangeWxConfig(pay) && handleEditPay()"
            >
              <div class="config-card-head">
                <span class="config-card-title">微信支付配置</span>
                <a-tag :color="pay.mchId ? 'green' : 'gray'">
                  {{ pay.mchId ? '已配置' : '未配置' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div v-if="pay.mchId" class="config-line">商户号：{{ pay.mchId }}</div>
                <div v-if="pay.mchId" class="config-line">
                  状态：<a-tag size="small" :color="pay.isActive === 1 ? 'green' : 'gray'">
                    {{ pay.isActive === 1 ? '启用' : '停用' }}
                  </a-tag>
                </div>
                <div v-else class="config-empty">点击配置商户号 / APIv3 密钥 / 证书</div>
              </div>
              <div class="config-card-foot">
                <template v-if="canChangeWxConfig(pay)"><icon-edit /> 点击编辑</template>
                <span v-else>仅查看</span>
              </div>
            </a-card>

            <!-- 卡片:虚拟支付配置(道具直购;与传统支付启用互斥) -->
            <a-card
              v-if="canReadWxConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canChangeWxConfig(vpay) }"
              :hoverable="canChangeWxConfig(vpay)"
              @click="canChangeWxConfig(vpay) && handleEditVpay()"
            >
              <div class="config-card-head">
                <span class="config-card-title">虚拟支付配置</span>
                <a-tag :color="vpayConfigured ? 'green' : 'orange'">
                  {{ vpayConfigured ? '可启用' : '配置不完整' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div v-if="vpay.offerId" class="config-line">OfferId：{{ vpay.offerId }}</div>
                <div v-if="vpay.offerId" class="config-line">
                  环境：<a-tag size="small" :color="vpay.env === 1 ? 'orange' : 'blue'">
                    {{ vpay.env === 1 ? '沙箱' : '现网' }}
                  </a-tag>
                  状态：<a-tag size="small" :color="vpay.isActive === 1 ? 'green' : 'gray'">
                    {{ vpay.isActive === 1 ? '启用' : '停用' }}
                  </a-tag>
                </div>
                <div v-else class="config-empty">点击配置 OfferId / AppKey / 消息推送</div>
              </div>
              <div class="config-card-foot">
                <template v-if="canChangeWxConfig(vpay)"><icon-edit /> 点击编辑</template>
                <span v-else>仅查看</span>
              </div>
            </a-card>

            <!-- 卡片:分享设置(小程序分享标题/卡片图,存 sys_config) -->
            <a-card
              v-if="canReadShareConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canEditShareConfig }"
              :hoverable="canEditShareConfig"
              @click="canEditShareConfig && handleEditShare()"
            >
              <div class="config-card-head">
                <span class="config-card-title">分享设置</span>
                <a-tag :color="share.image ? 'green' : 'gray'">
                  {{ share.image ? '已配置' : '未配置' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div v-if="share.title" class="config-line">标题：{{ share.title }}</div>
                <img v-if="share.image" class="share-thumb" :src="share.image" alt="分享卡片图" />
                <div v-if="!share.title && !share.image" class="config-empty">点击配置分享标题 / 卡片图(5:4)</div>
              </div>
              <div class="config-card-foot">
                <template v-if="canEditShareConfig"><icon-edit /> 点击编辑</template>
                <span v-else>仅查看</span>
              </div>
            </a-card>

            <!-- 卡片:全局提示词(三层提示词的全局层,所有对话生效;存 global_prompt) -->
            <a-card
              v-if="canReadGlobalPrompt"
              class="config-card"
              :class="{ 'config-card-readonly': !canEditGlobalPrompt }"
              :hoverable="canEditGlobalPrompt"
              @click="canEditGlobalPrompt && handleEditPrompt()"
            >
              <div class="config-card-head">
                <span class="config-card-title">全局提示词</span>
                <a-tag :color="globalPrompt.content ? 'green' : 'gray'">
                  {{ globalPrompt.content ? '已配置' : '未配置' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div v-if="globalPrompt.content" class="config-prompt-preview">{{ globalPrompt.content }}</div>
                <div v-else class="config-empty">点击设置所有对话生效的全局提示词</div>
              </div>
              <div class="config-card-foot">
                <template v-if="canEditGlobalPrompt"><icon-edit /> 点击编辑</template>
                <span v-else>仅查看</span>
              </div>
            </a-card>

            <!-- 卡片:独立 AI 生成页面总开关 -->
            <a-card
              v-if="canReadGenerationConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canEditGenerationConfig }"
            >
              <div class="config-card-head">
                <span class="config-card-title">AI 生成功能</span>
                <a-tag :color="generationConfig.generationEnabled ? 'green' : 'gray'">
                  {{ generationConfig.generationEnabled ? '已启用' : '已关闭' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div class="config-line">控制小程序独立生成页面及提交接口。</div>
                <div class="config-line">关闭后显示“当前功能正在开发中”。</div>
              </div>
              <div class="config-card-foot config-switch-foot">
                <span>{{ canEditGenerationConfig ? '功能状态' : '仅查看' }}</span>
                <a-switch
                  v-if="canEditGenerationConfig"
                  :model-value="generationConfig.generationEnabled"
                  :loading="generationConfigSaving"
                  @change="handleGenerationEnabledChange"
                />
              </div>
            </a-card>

            <!-- 卡片:生成图片保存水印 -->
            <a-card
              v-if="canReadGenerationConfig"
              class="config-card"
              :class="{ 'config-card-readonly': !canEditGenerationConfig }"
            >
              <div class="config-card-head">
                <span class="config-card-title">下载水印</span>
                <a-tag :color="generationConfig.downloadWatermarkEnabled ? 'green' : 'orange'">
                  {{ generationConfig.downloadWatermarkEnabled ? '包含 AI 标识' : '保存原图' }}
                </a-tag>
              </div>
              <div class="config-card-body">
                <div class="config-line">开启后，保存到相册的图片带“AI生成”水印。</div>
                <div class="config-line">关闭存在内容标识合规风险。</div>
              </div>
              <div class="config-card-foot config-switch-foot">
                <span>{{ canEditGenerationConfig ? '水印状态' : '仅查看' }}</span>
                <a-switch
                  v-if="canEditGenerationConfig"
                  :model-value="generationConfig.downloadWatermarkEnabled"
                  :loading="generationConfigSaving"
                  @change="handleDownloadWatermarkChange"
                />
              </div>
            </a-card>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 小程序配置编辑弹窗 -->
    <a-modal
      v-model:visible="miniAppOpen"
      title="编辑小程序配置"
      :width="640"
      @before-ok="submitMiniApp"
      @cancel="miniAppOpen = false"
    >
      <a-form ref="miniAppFormRef" :model="miniAppForm" :rules="miniAppRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="应用名称" field="appName">
          <a-input v-model="miniAppForm.appName" placeholder="例如:极光小程序" />
        </a-form-item>
        <a-form-item label="AppId" field="appId">
          <a-input v-model="miniAppForm.appId" placeholder="wx 开头的小程序 AppId" />
        </a-form-item>
        <a-form-item label="AppSecret" field="appSecret">
          <a-input-password v-model="miniAppForm.appSecret" placeholder="不修改请留空(显示 **** 表示已配置)" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="状态" field="isActive">
          <a-radio-group v-model="miniAppForm.isActive">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 微信支付配置编辑弹窗 -->
    <a-modal
      v-model:visible="payOpen"
      title="编辑微信支付配置"
      :width="720"
      @before-ok="submitPay"
      @cancel="payOpen = false"
    >
      <a-form ref="payFormRef" :model="payForm" :rules="payRules"
              :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="应用名称" field="appName">
          <a-input v-model="payForm.appName" placeholder="例如:极光支付" />
        </a-form-item>
        <a-form-item label="AppId" field="appId">
          <a-input v-model="payForm.appId" placeholder="关联的小程序/公众号 AppId" />
        </a-form-item>
        <a-form-item label="商户号" field="mchId">
          <a-input v-model="payForm.mchId" placeholder="微信支付商户号" />
        </a-form-item>
        <a-form-item label="APIv3 密钥" field="apiV3Key">
          <a-input-password v-model="payForm.apiV3Key" placeholder="不修改请留空(显示 **** 表示已配置)" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="证书私钥" field="privateKey">
          <a-textarea v-model="payForm.privateKey" placeholder="apiclient_key.pem 内容,不修改请留空" :auto-size="{ minRows: 3, maxRows: 6 }" />
        </a-form-item>
        <a-form-item label="证书" field="privateCert">
          <a-textarea v-model="payForm.privateCert" placeholder="apiclient_cert.pem 内容,不修改请留空" :auto-size="{ minRows: 3, maxRows: 6 }" />
        </a-form-item>
        <a-form-item label="微信支付公钥ID" field="publicKeyId">
          <a-input v-model="payForm.publicKeyId" placeholder="PUB_KEY_ID_ 开头(公钥模式必填)" allow-clear />
        </a-form-item>
        <a-form-item label="微信支付公钥" field="publicKey">
          <a-textarea v-model="payForm.publicKey" placeholder="pub_key.pem 内容(公钥模式必填)" :auto-size="{ minRows: 3, maxRows: 6 }" />
        </a-form-item>
        <a-form-item label="回调地址" field="notifyUrl">
          <a-input v-model="payForm.notifyUrl" placeholder="支付结果回调 URL" />
        </a-form-item>
        <a-form-item label="状态" field="isActive">
          <a-radio-group v-model="payForm.isActive">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 虚拟支付配置编辑弹窗 -->
    <a-modal
      v-model:visible="vpayOpen"
      title="编辑虚拟支付配置"
      :width="680"
      @before-ok="submitVpay"
      @cancel="vpayOpen = false"
    >
      <a-alert type="info" style="margin-bottom: 16px;">
        公众平台消息推送请使用安全模式、JSON 格式，URL：{{ vpayCallbackUrl }}
      </a-alert>
      <a-form ref="vpayFormRef" :model="vpayForm" :rules="vpayRules"
              :label-col-props="{ span: 7 }" :wrapper-col-props="{ span: 17 }">
        <a-form-item label="小程序 AppId" field="appId">
          <a-input v-model="vpayForm.appId" placeholder="虚拟支付所在小程序的 AppId" />
        </a-form-item>
        <a-form-item label="OfferId" field="offerId">
          <a-input v-model="vpayForm.offerId" placeholder="虚拟支付商户号(虚拟支付-基本配置)" />
        </a-form-item>
        <a-form-item label="现网 AppKey" field="prodAppKey">
          <a-input-password v-model="vpayForm.prodAppKey" placeholder="env=0 用;不修改请留空(**** 表示已配置)" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="沙箱 AppKey" field="sandboxAppKey">
          <a-input-password v-model="vpayForm.sandboxAppKey" placeholder="env=1 用;不修改请留空" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="当前环境" field="env">
          <a-radio-group v-model="vpayForm.env">
            <a-radio :value="0">现网</a-radio>
            <a-radio :value="1">沙箱(iOS 不支持)</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="消息推送 Token" field="pushToken">
          <a-input-password v-model="vpayForm.pushToken" placeholder="开发设置-消息推送 Token;不修改请留空" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="EncodingAESKey" field="pushAesKey">
          <a-input-password v-model="vpayForm.pushAesKey" placeholder="消息推送 EncodingAESKey;不修改请留空" allow-clear autocomplete="new-password" />
        </a-form-item>
        <a-form-item label="启用" field="isActive">
          <a-radio-group v-model="vpayForm.isActive">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
          <template #extra>
            <span style="color: var(--color-text-3); font-size: 12px;">启用虚拟支付将自动停用传统微信支付(渠道互斥)</span>
          </template>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分享设置编辑弹窗 -->
    <a-modal
      v-model:visible="shareOpen"
      title="编辑分享设置"
      :width="560"
      @before-ok="submitShare"
      @cancel="shareOpen = false"
    >
      <a-form :model="shareForm" :label-col-props="{ span: 6 }" :wrapper-col-props="{ span: 18 }">
        <a-form-item label="分享标题" field="title">
          <a-input v-model="shareForm.title" placeholder="分享到微信时的卡片标题" :max-length="30" allow-clear />
        </a-form-item>
        <a-form-item label="卡片图" field="image">
          <div>
            <a-upload
              :auto-upload="true"
              :show-file-list="false"
              accept="image/*"
              :custom-request="handleShareImageUpload"
            >
              <template #upload-button>
                <div class="share-uploader" title="点击上传分享卡片图">
                  <img v-if="shareForm.image" class="share-uploader-img" :src="shareForm.image" alt="分享卡片图" />
                  <span v-else>+ 上传</span>
                </div>
              </template>
            </a-upload>
            <div class="share-upload-tip">
              会话分享卡官方比例 5:4(建议 500×400);分享到朋友圈复用同图、微信自动裁中间 1:1;留空则微信自动用页面截图
            </div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 全局提示词编辑弹窗 -->
    <a-modal
      v-model:visible="promptOpen"
      title="编辑全局提示词"
      :width="720"
      :body-style="{ maxHeight: '68vh', overflowY: 'auto' }"
      @before-ok="submitPrompt"
      @cancel="promptOpen = false"
    >
      <a-textarea
        v-model="promptForm.content"
        placeholder="所有对话生效的全局提示词(三层提示词的全局层)"
        :auto-size="{ minRows: 10, maxRows: 24 }"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconEdit } from '@arco-design/web-vue/es/icon';
import { listWxConfig, addWxConfig, updateWxConfig, SysWxConfig } from '@/api/mobile';
import { getShareConfig as fetchShareConfig, updateShareConfig, ShareConfig } from '@/api/shareConfig';
import { uploadAvatar } from '@/api/system';
import { getActiveGlobalPrompt, saveGlobalPrompt } from '@/api/globalPrompt';
import {
  GenerationConfig,
  getGenerationConfig as fetchGenerationConfig,
  updateGenerationConfig
} from '@/api/generationConfig';
import { usePermissionStore } from '@/stores/permission';

const permissionStore = usePermissionStore();
const canReadWxConfig = computed(() => permissionStore.hasAnyPermission('system:wxconfig:list'));
const canAddWxConfig = computed(() => permissionStore.hasAnyPermission('system:wxconfig:add'));
const canEditWxConfig = computed(() => permissionStore.hasAnyPermission('system:wxconfig:edit'));
const canReadShareConfig = computed(() => permissionStore.hasAnyPermission(['system:shareconfig:list', 'system:shareconfig:edit']));
const canEditShareConfig = computed(() => permissionStore.hasAnyPermission('system:shareconfig:edit'));
const canReadGlobalPrompt = computed(() => permissionStore.hasAnyPermission(['globalprompt:list', 'globalprompt:edit']));
const canEditGlobalPrompt = computed(() => permissionStore.hasAnyPermission('globalprompt:edit'));
const canReadGenerationConfig = computed(() => permissionStore.hasAnyPermission([
  'system:generationconfig:list',
  'system:generationconfig:edit'
]));
const canEditGenerationConfig = computed(() => permissionStore.hasAnyPermission('system:generationconfig:edit'));

// 微信配置(按 appType 拆为小程序 / 支付 / 虚拟支付,空对象代表尚未配置)
const miniApp = ref<SysWxConfig>({});
const pay = ref<SysWxConfig>({});
const vpay = ref<SysWxConfig>({});

function isNonBlank(value?: string): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isValidPushToken(value?: string): boolean {
  if (!isNonBlank(value)) return false;
  if (value.includes('****')) return true;
  return value.length >= 3 && value.length <= 32;
}

function isValidPushAesKey(value?: string): boolean {
  if (!isNonBlank(value)) return false;
  if (value.includes('****')) return true;
  return value.replace(/ /g, '').length === 43;
}

const vpayConfigured = computed(() => {
  const config = vpay.value;
  const envValid = config.env === 0 || config.env === 1;
  const activeMiniAppId = miniApp.value.isActive === 1 ? miniApp.value.appId : undefined;
  const selectedAppKey = config.env === 1 ? config.sandboxAppKey : config.prodAppKey;
  return Boolean(
    envValid
    && isNonBlank(config.appId)
    && isNonBlank(activeMiniAppId)
    && config.appId === activeMiniAppId
    && isNonBlank(config.offerId)
    && isNonBlank(selectedAppKey)
    && isValidPushToken(config.pushToken)
    && isValidPushAesKey(config.pushAesKey)
  );
});

function getWxConfigList() {
  return listWxConfig({ pageNum: 1, pageSize: 100 }).then((res: any) => {
    const records: SysWxConfig[] = res.records || [];
    miniApp.value = records.find(r => r.appType === 'mini_app' && r.isActive === 1)
      || records.find(r => r.appType === 'mini_app')
      || {};
    pay.value = records.find(r => r.appType === 'pay') || {};
    vpay.value = records.find(r => r.appType === 'virtual_pay') || {};
  });
}

function canChangeWxConfig(config: SysWxConfig) {
  return config.wxId ? canEditWxConfig.value : canAddWxConfig.value;
}

// ---- 小程序配置编辑 ----
const miniAppOpen = ref(false);
const miniAppFormRef = ref();
const miniAppForm = ref<SysWxConfig>({});
const miniAppRules = {
  appId: [{ required: true, message: '请输入 AppId' }]
};

function handleEditMiniApp() {
  // 回显:敏感项 appSecret 后端已脱敏,留空提交则后端跳过更新
  miniAppForm.value = {
    wxId: miniApp.value.wxId,
    appType: 'mini_app',
    appName: miniApp.value.appName,
    appId: miniApp.value.appId,
    appSecret: '',
    isActive: miniApp.value.isActive ?? 1
  };
  miniAppOpen.value = true;
}

function submitMiniApp(done: (closed: boolean) => void) {
  miniAppFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const data = { ...miniAppForm.value, appType: 'mini_app' };
    const action = data.wxId ? updateWxConfig(data) : addWxConfig(data);
    action.then(() => {
      Message.success('保存成功');
      void getWxConfigList().catch(() => undefined);
      done(true);
    }).catch(() => done(false));
  });
}

// ---- 微信支付配置编辑 ----
const payOpen = ref(false);
const payFormRef = ref();
const payForm = ref<SysWxConfig>({});
const payRules = {
  appId: [{ required: true, message: '请输入 AppId' }],
  mchId: [{ required: true, message: '请输入商户号' }]
};

function handleEditPay() {
  // 敏感项回显后端脱敏值(****);不修改则提交时 resolveSensitive 检测到 **** 跳过更新、保留原密文
  payForm.value = {
    wxId: pay.value.wxId,
    appType: 'pay',
    appName: pay.value.appName,
    appId: pay.value.appId,
    mchId: pay.value.mchId,
    apiV3Key: pay.value.apiV3Key,
    privateKey: pay.value.privateKey,
    privateCert: pay.value.privateCert,
    notifyUrl: pay.value.notifyUrl,
    publicKeyId: pay.value.publicKeyId,
    publicKey: pay.value.publicKey,
    isActive: pay.value.isActive ?? 1
  };
  payOpen.value = true;
}

function submitPay(done: (closed: boolean) => void) {
  payFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const data = { ...payForm.value, appType: 'pay' };
    const action = data.wxId ? updateWxConfig(data) : addWxConfig(data);
    action.then(() => {
      Message.success('保存成功');
      void getWxConfigList().catch(() => undefined);
      done(true);
    }).catch(() => done(false));
  });
}

// ---- 虚拟支付配置编辑(道具直购;敏感项脱敏回显,留空跳过更新;启用与传统互斥,后端处理) ----
const vpayOpen = ref(false);
const vpayFormRef = ref();
const vpayForm = ref<SysWxConfig>({});
const vpayCallbackUrl = `${window.location.origin}/api/mobile/wx/msg-push`;
const vpayRules = {
  appId: [{ required: true, message: '请输入小程序 AppId' }],
  offerId: [{ required: true, message: '请输入 OfferId' }]
};

function handleEditVpay() {
  vpayForm.value = {
    wxId: vpay.value.wxId,
    appType: 'virtual_pay',
    appName: vpay.value.appName || '虚拟支付',
    appId: vpay.value.appId,
    offerId: vpay.value.offerId,
    prodAppKey: vpay.value.prodAppKey,
    sandboxAppKey: vpay.value.sandboxAppKey,
    env: vpay.value.env ?? 1,
    pushToken: vpay.value.pushToken,
    pushAesKey: vpay.value.pushAesKey,
    isActive: vpay.value.isActive ?? 0
  };
  vpayOpen.value = true;
}

function submitVpay(done: (closed: boolean) => void) {
  vpayFormRef.value.validate((errors: any) => {
    if (errors) {
      done(false);
      return;
    }
    const data = { ...vpayForm.value, appType: 'virtual_pay' };
    const action = data.wxId ? updateWxConfig(data) : addWxConfig(data);
    action.then(() => {
      Message.success('保存成功');
      void getWxConfigList().catch(() => undefined);
      done(true);
    }).catch(() => done(false));
  });
}

// ---- 分享设置(后端仅允许维护 mobile.share.title / mobile.share.image) ----
const share = ref<ShareConfig>({});

function getShareConfig() {
  return fetchShareConfig().then((res: any) => {
    share.value = {
      title: res?.title || '',
      image: res?.image || ''
    };
  });
}

const shareOpen = ref(false);
const shareForm = ref<{ title?: string; image?: string }>({});

function handleEditShare() {
  shareForm.value = { title: share.value.title, image: share.value.image };
  shareOpen.value = true;
}

// 卡片图上传:复用后台图片上传接口,成功回填 /profile URL 预览
async function handleShareImageUpload(option: any) {
  try {
    const fileUrl = await uploadAvatar(option.fileItem.file);
    shareForm.value.image = fileUrl as unknown as string;
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '图片上传失败');
    option.onError(error);
  }
}

function submitShare(done: (closed: boolean) => void) {
  updateShareConfig({
    title: shareForm.value.title || '',
    image: shareForm.value.image || ''
  }).then(() => {
    Message.success('保存成功');
    void getShareConfig().catch(() => undefined);
    done(true);
  }).catch(() => done(false));
}

// ---- 全局提示词(三层提示词的全局层,所有对话生效;复用 global_prompt 的 active/save)----
const globalPrompt = ref<{ content?: string }>({});

function getGlobalPrompt() {
  return getActiveGlobalPrompt().then((res: any) => {
    globalPrompt.value = { content: res?.content || '' };
  });
}

const promptOpen = ref(false);
const promptForm = ref<{ content?: string }>({});

function handleEditPrompt() {
  promptForm.value = { content: globalPrompt.value.content };
  promptOpen.value = true;
}

function submitPrompt(done: (closed: boolean) => void) {
  if (!promptForm.value.content || !promptForm.value.content.trim()) {
    Message.warning('请输入全局提示词内容');
    done(false);
    return;
  }
  saveGlobalPrompt({ content: promptForm.value.content }).then(() => {
    Message.success('保存成功');
    void getGlobalPrompt().catch(() => undefined);
    done(true);
  }).catch(() => done(false));
}

// ---- AI 生成页面与下载水印固定配置 ----
const generationConfig = ref<GenerationConfig>({
  generationEnabled: true,
  downloadWatermarkEnabled: true
});
const generationConfigSaving = ref(false);

function getGenerationConfig() {
  return fetchGenerationConfig().then((res: any) => {
    generationConfig.value = {
      generationEnabled: res?.generationEnabled !== false,
      downloadWatermarkEnabled: res?.downloadWatermarkEnabled !== false
    };
  });
}

function saveGenerationConfig(next: GenerationConfig, successMessage: string) {
  generationConfigSaving.value = true;
  return updateGenerationConfig(next)
    .then(() => {
      generationConfig.value = next;
      Message.success(successMessage);
    })
    .finally(() => {
      generationConfigSaving.value = false;
    });
}

function handleGenerationEnabledChange(enabled: boolean) {
  const save = () => saveGenerationConfig({
    ...generationConfig.value,
    generationEnabled: enabled
  }, enabled ? 'AI 生成功能已启用' : 'AI 生成功能已关闭');
  if (enabled) {
    void save();
    return;
  }
  Modal.confirm({
    title: '确认关闭 AI 生成功能',
    content: '关闭后小程序独立生成页面将显示“当前功能正在开发中”，并禁止提交生成任务。',
    onOk: save
  });
}

function handleDownloadWatermarkChange(enabled: boolean) {
  const save = () => saveGenerationConfig({
    ...generationConfig.value,
    downloadWatermarkEnabled: enabled
  }, enabled ? '下载水印已开启' : '下载水印已关闭');
  if (enabled) {
    void save();
    return;
  }
  Modal.confirm({
    title: '确认关闭下载水印',
    content: '关闭后用户保存到相册的生成图片将不包含 AI 标识，可能存在内容标识合规风险。',
    okButtonProps: { status: 'danger' },
    onOk: save
  });
}

watch(canReadWxConfig, allowed => {
  if (allowed) void getWxConfigList().catch(() => undefined);
}, { immediate: true });
watch(canReadShareConfig, allowed => {
  if (allowed) void getShareConfig().catch(() => undefined);
}, { immediate: true });
watch(canReadGlobalPrompt, allowed => {
  if (allowed) void getGlobalPrompt().catch(() => undefined);
}, { immediate: true });
watch(canReadGenerationConfig, allowed => {
  if (allowed) void getGenerationConfig().catch(() => undefined);
}, { immediate: true });
</script>

<style scoped>
.app-container {
  padding: 0px;
}

/* 正方形卡片网格:一排 5 个,窄屏自适应降列;后续新增功能卡直接往格子里加 */
.config-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

@media (max-width: 1400px) {
  .config-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 1100px) {
  .config-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 800px) {
  .config-grid { grid-template-columns: repeat(2, 1fr); }
}

.config-card {
  cursor: pointer;
  transition: box-shadow 0.2s;
  /* 紧凑固定高度(正方形留白过多,按用户反馈压低);同排卡片等高 */
  height: 200px;
}

.config-card-readonly {
  cursor: default;
}

/* 卡片内部纵向撑满:头部固定、正文伸缩、脚部沉底 */
.config-card :deep(.arco-card-body) {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.config-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.config-card-title {
  font-size: 15px;
  font-weight: 600;
}

.config-card-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 13px;
}

.config-line {
  margin-bottom: 6px;
  word-break: break-all;
}

.config-empty {
  color: var(--color-text-3);
}

/* 全局提示词卡片内容预览:多行截断 */
.config-prompt-preview {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}

.config-card-foot {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--color-neutral-3);
  color: rgb(var(--primary-6));
  font-size: 13px;
}

.config-switch-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 分享卡片图缩略预览(卡片内) */
.share-thumb {
  width: 100%;
  max-height: 72px;
  object-fit: cover;
  border-radius: 4px;
  margin-top: 4px;
}

/* 弹窗内上传框:150×120 = 5:4 */
.share-uploader {
  width: 150px;
  height: 120px;
  border: 1px dashed var(--color-neutral-4);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
  background: var(--color-fill-1);
  overflow: hidden;
  cursor: pointer;
}

.share-uploader-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.share-upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.6;
}
</style>
