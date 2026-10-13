<template>
  <div class="user-settings">
    <a-card class="settings-card" title="用户设置">
      <!-- 标签页 -->
      <a-tabs v-model:active-key="activeTab">
        <!-- 基本信息 -->
        <a-tab-pane key="basic" title="基本信息">
          <a-form :model="basicForm" :label-col-props="{ span: 4 }" :wrapper-col-props="{ span: 20 }" layout="horizontal">
            <a-form-item label="用户名">
              <a-input 
                v-model="basicForm.username" 
                placeholder="请输入用户名" 
                :disabled="basicForm.userId === 1"
              />
              <template v-if="basicForm.userId === 1">
                <div style="color: #86909c; font-size: 12px; margin-top: 4px;">超级管理员用户名不可修改</div>
              </template>
            </a-form-item>
            <a-form-item label="昵称">
              <a-input v-model="basicForm.nickName" placeholder="请输入昵称" />
            </a-form-item>
            <a-form-item label="邮箱">
              <a-input v-model="basicForm.email" placeholder="请输入邮箱" />
            </a-form-item>
            <a-form-item label="手机号">
              <a-input v-model="basicForm.phonenumber" placeholder="请输入手机号" />
            </a-form-item>
            <a-form-item label="头像">
              <a-upload
                :file-list="avatarFileList"
                :limit="1"
                :auto-upload="true"
                :custom-request="handleAvatarUpload"
                accept="image/*"
              >
                <template #upload-button>
                  <a-avatar :size="64" :image-url="getFullAvatarUrl(basicForm.avatar)">
                    <template #trigger-icon><icon-camera /></template>
                  </a-avatar>
                </template>
              </a-upload>
            </a-form-item>
          </a-form>
          <div class="form-footer">
            <a-button type="primary" @click="handleSaveBasic">
              <template #icon><icon-save /></template>
              保存
            </a-button>
          </div>
        </a-tab-pane>

        <!-- 安全设置 -->
        <a-tab-pane key="security" title="安全设置">
          <a-form :model="securityForm" :label-col-props="{ span: 4 }" :wrapper-col-props="{ span: 20 }" layout="horizontal">
            <a-form-item label="原密码">
              <a-input-password v-model="securityForm.oldPassword" placeholder="请输入原密码" />
            </a-form-item>
            <a-form-item label="新密码">
              <a-input-password v-model="securityForm.newPassword" placeholder="请输入新密码" />
            </a-form-item>
            <a-form-item label="确认密码">
              <a-input-password v-model="securityForm.confirmPassword" placeholder="请再次输入新密码" />
            </a-form-item>
          </a-form>
          <div class="form-footer">
            <a-button type="primary" @click="handleSaveSecurity">
              <template #icon><icon-save /></template>
              保存
            </a-button>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import {
  IconCamera,
  IconSave,
} from '@arco-design/web-vue/es/icon';
import type { FileItem } from '@arco-design/web-vue';
import { getInfo } from '@/api/login';
import { updateUser, updatePassword, uploadAvatar } from '@/api/system';
import { useUserStore } from '@/stores/user';

const activeTab = ref('basic');
const avatarFileList = ref<FileItem[]>([]);
const userStore = useUserStore();

const basicForm = reactive({
  userId: 0,
  username: '',
  nickName: '',
  email: '',
  phonenumber: '',
  avatar: ''
});

const securityForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

// 页面加载时获取当前用户信息
onMounted(async () => {
  try {
    const data: any = await getInfo();
    if (data && data.user) {
      basicForm.userId = data.user.userId;
      basicForm.username = data.user.userName || '';
      basicForm.nickName = data.user.nickName || '';
      basicForm.email = data.user.email || '';
      basicForm.phonenumber = data.user.phonenumber || '';
      basicForm.avatar = data.user.avatar || '';
    }
  } catch (error) {
    console.error('获取用户信息失败:', error);
  }
});



async function handleAvatarUpload(option: any) {
  try {
    const file = option.fileItem.file;
    const fileUrl = await uploadAvatar(file);
    basicForm.avatar = fileUrl;
    Message.success('头像上传成功');
    option.onSuccess(fileUrl);
  } catch (error: any) {
    Message.error(error?.message || '头像上传失败');
    option.onError(error);
  }
}

// 获取完整的头像 URL
function getFullAvatarUrl(avatar: string): string {
  if (!avatar) return '';
  
  // 如果是相对路径，拼接完整 URL
  if (avatar.startsWith('/profile/')) {
    return '/api' + avatar;
  }
  
  return avatar;
}

async function handleSaveBasic() {
  try {
    const data = {
      user: {
        userId: basicForm.userId,
        userName: basicForm.username,
        nickName: basicForm.nickName,
        email: basicForm.email,
        phonenumber: basicForm.phonenumber,
        avatar: basicForm.avatar
      },
      roleIds: []
    };
    await updateUser(data);
    Message.success('基本信息保存成功');

    // 刷新用户信息并更新store
    const userInfo = await getInfo();
    if (userInfo && userInfo.user) {
      userStore.setUserInfo(userInfo.user);
    }
  } catch (error: any) {
    Message.error(error?.message || '保存失败');
  }
}

async function handleSaveSecurity() {
  if (!securityForm.oldPassword || !securityForm.newPassword || !securityForm.confirmPassword) {
    Message.warning('请填写完整的密码信息');
    return;
  }
  if (securityForm.newPassword !== securityForm.confirmPassword) {
    Message.warning('两次输入的密码不一致');
    return;
  }
  if (securityForm.oldPassword === securityForm.newPassword) {
    Message.warning('新密码不能与原密码相同');
    return;
  }

  try {
    await updatePassword({
      oldPassword: securityForm.oldPassword,
      newPassword: securityForm.newPassword
    });
    Message.success('密码修改成功');
    securityForm.oldPassword = '';
    securityForm.newPassword = '';
    securityForm.confirmPassword = '';
  } catch (error: any) {
    Message.error(error?.message || '修改密码失败');
  }
}
</script>

<style scoped>
.user-settings {
  padding: 0;
}

.settings-card {
  /* 移除宽度限制，让卡片占满整个宽度 */
}

.form-footer {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border-2);
  text-align: left;
}

:deep(.a-form-item) {
  margin-bottom: 24px;
}
</style>

