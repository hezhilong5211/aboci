package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.vo.DistributionViews;

public interface IDistributionQrcodeService {
    DistributionViews.Qrcode getQrcode(Long partnerId);
}
