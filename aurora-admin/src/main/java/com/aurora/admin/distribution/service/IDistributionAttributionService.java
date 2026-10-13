package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.vo.DistributionViews;

public interface IDistributionAttributionService {
    void recordVisit(DistributionDtos.Visit request);

    DistributionViews.BindResult bind(Long userId, DistributionDtos.Bind request);
}
