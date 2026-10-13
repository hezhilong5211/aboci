import { defineStore } from 'pinia';

interface UserInfo {
  userId: number;
  userName: string;
  nickName: string;
  email: string;
  phonenumber: string;
  avatar: string;
}

interface UserState {
  userInfo: UserInfo | null;
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    userInfo: null,
  }),

  getters: {
    // 获取用户头像
    getUserAvatar: (state) => state.userInfo?.avatar || '',
    // 获取用户昵称
    getNickName: (state) => state.userInfo?.nickName || state.userInfo?.userName || '',
  },

  actions: {
    // 设置用户信息
    setUserInfo(userInfo: UserInfo | null) {
      this.userInfo = userInfo;
    },

    // 更新用户信息
    updateUserInfo(partialInfo: Partial<UserInfo>) {
      if (this.userInfo) {
        this.userInfo = {
          ...this.userInfo,
          ...partialInfo,
        };
      }
    },
  },
});

