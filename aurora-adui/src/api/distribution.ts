import request from '@/utils/request';

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export interface PartnerRow {
  partnerId: number;
  partnerNo: string;
  userId: number;
  userNo: string;
  nickName: string;
  phoneMasked: string;
  avatarUrl?: string;
  status: string;
  enabledTime?: string;
  disabledTime?: string;
  visitCount: number;
  promotedUserCount: number;
  paidUserCount: number;
  validOrderCount: number;
  validOrderAmount: number;
  validCommissionAmount: number;
  availableAmount: number;
  lockedAmount: number;
  withdrawnAmount: number;
  debtAmount: number;
  inviteCode?: string;
  remark?: string;
  createTime?: string;
}

export interface WithdrawalRow {
  withdrawalId: number;
  withdrawalNo: string;
  partnerId: number;
  partnerNo: string;
  partnerName: string;
  phoneMasked: string;
  applyAmount: number;
  methodType: string;
  payeeName: string;
  bankName?: string;
  bankAccountMasked?: string;
  receiptAvailable: boolean;
  status: string;
  rejectReason?: string;
  paymentReferenceMasked?: string;
  paymentProofAvailable: boolean;
  applyTime: string;
  auditTime?: string;
  paidTime?: string;
  auditBy?: string;
  payBy?: string;
  transactions?: TransactionRow[];
}

export interface WithdrawalSensitive {
  withdrawalId: number;
  methodType: string;
  payeeName: string;
  bankName?: string;
  bankAccount?: string;
  receiptImageData?: string;
  paymentProofImageData?: string;
}

export interface WithdrawalPaymentProfile {
  withdrawalId: number;
  methodType: string;
  payeeName: string;
  bankName?: string;
  bankAccount?: string;
  receiptImageData?: string;
}

export interface WithdrawalReceiptThumbnail {
  withdrawalId: number;
  imageData: string;
}

export interface CommissionRow {
  commissionId: number;
  orderNo: string;
  partnerId: number;
  partnerNo: string;
  partnerName: string;
  userNo: string;
  phoneMasked: string;
  productType: string;
  productName: string;
  orderAmount: number;
  rateBps: number;
  commissionAmount: number;
  availableAmount: number;
  lockedAmount: number;
  withdrawnAmount: number;
  reversedAmount: number;
  debtOffsetAmount: number;
  status: string;
  payTime: string;
  refundNo?: string;
  refundTime?: string;
  transactions?: TransactionRow[];
}

export interface TransactionRow {
  transactionId: number;
  type: string;
  changeAmount: number;
  availableAfter: number;
  lockedAfter: number;
  debtAfter: number;
  bizNo: string;
  sourceOrderNo?: string;
  withdrawalNo?: string;
  remark: string;
  createTime: string;
}

export interface OverviewData {
  partnerCount: number;
  promotedUserCount: number;
  paidUserCount: number;
  validOrderCount: number;
  validOrderAmount: number;
  validCommissionAmount: number;
  unwithdrawnAmount: number;
  withdrawnAmount: number;
  debtAmount: number;
  recentWithdrawals: WithdrawalRow[];
  ranking: PartnerRow[];
}

export interface DistributionConfig {
  enabled: number;
  commissionRateBps: number;
  minWithdrawAmount: number;
  updateBy?: string;
  updateTime?: string;
}

export function getOverview(): Promise<OverviewData> {
  return request({ url: '/system/distribution/overview', method: 'get' });
}

export function listPartners(params: any): Promise<PageResult<PartnerRow>> {
  return request({ url: '/system/distribution/partner/list', method: 'get', params });
}

export function listPartnerCandidates(params: any): Promise<PageResult<any>> {
  return request({ url: '/system/distribution/partner/candidates', method: 'get', params });
}

export function getPartner(id: number): Promise<PartnerRow> {
  return request({ url: `/system/distribution/partner/${id}`, method: 'get' });
}

export function addPartner(data: { userId: number; remark?: string }): Promise<number> {
  return request({ url: '/system/distribution/partner', method: 'post', data });
}

export function updatePartnerStatus(id: number, data: { status: string; remark?: string }): Promise<void> {
  return request({ url: `/system/distribution/partner/${id}/status`, method: 'put', data });
}

export function listPartnerUsers(id: number, params: any): Promise<PageResult<any>> {
  return request({ url: `/system/distribution/partner/${id}/users`, method: 'get', params });
}

export function listPartnerOrders(id: number, params: any): Promise<PageResult<any>> {
  return request({ url: `/system/distribution/partner/${id}/orders`, method: 'get', params });
}

export function getPartnerQrcode(id: number): Promise<any> {
  return request({ url: `/system/distribution/partner/${id}/qrcode`, method: 'get' });
}

export function listCommissions(params: any): Promise<PageResult<CommissionRow>> {
  return request({ url: '/system/distribution/commission/list', method: 'get', params });
}

export function getCommission(id: number): Promise<CommissionRow> {
  return request({ url: `/system/distribution/commission/${id}`, method: 'get' });
}

export function adjustCommission(id: number, data: { effectiveAmount: number; reason: string }): Promise<void> {
  return request({ url: `/system/distribution/commission/${id}/adjust`, method: 'put', data });
}

export function listWithdrawals(params: any): Promise<PageResult<WithdrawalRow>> {
  return request({ url: '/system/distribution/withdrawal/list', method: 'get', params });
}

export function getWithdrawal(id: number): Promise<WithdrawalRow> {
  return request({ url: `/system/distribution/withdrawal/${id}`, method: 'get' });
}

export function getWithdrawalSensitive(id: number): Promise<WithdrawalSensitive> {
  return request({ url: `/system/distribution/withdrawal/${id}/sensitive`, method: 'get' });
}

export function getWithdrawalReceiptThumbnails(ids: number[]): Promise<WithdrawalReceiptThumbnail[]> {
  return request({
    url: '/system/distribution/withdrawal/receipt-thumbnails',
    method: 'get',
    params: { ids: ids.join(',') }
  });
}

export function getWithdrawalPaymentProfile(id: number): Promise<WithdrawalPaymentProfile> {
  return request({ url: `/system/distribution/withdrawal/${id}/payment-profile`, method: 'get' });
}

export function rejectWithdrawal(id: number, reason: string): Promise<void> {
  return request({ url: `/system/distribution/withdrawal/${id}/reject`, method: 'put', data: { reason } });
}

export function markWithdrawalPaid(id: number, paymentReference?: string, proof?: File): Promise<void> {
  const data = new FormData();
  if (paymentReference?.trim()) {
    data.append('paymentReference', paymentReference.trim());
  }
  if (proof) {
    data.append('proof', proof);
  }
  return request({ url: `/system/distribution/withdrawal/${id}/paid`, method: 'put', data });
}

export function getDistributionConfig(): Promise<DistributionConfig> {
  return request({ url: '/system/distribution/config', method: 'get' });
}

export function updateDistributionConfig(data: DistributionConfig): Promise<void> {
  return request({ url: '/system/distribution/config', method: 'put', data });
}
