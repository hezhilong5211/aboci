package com.aurora.admin.controller.monitor;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.model.monitor.Server;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控
 */
@RestController
@RequestMapping("/monitor/server")
public class ServerController {

    @GetMapping()
    @PreAuthorize("@ss.hasPermi('monitor:server:list')")
    public Result<Server> getInfo() throws Exception {
        Server server = new Server();
        server.copyTo();
        return Result.success(server);
    }
}
