package com.aurora.admin.controller.monitor;

import com.aurora.common.core.service.ISysOperLogService;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysOperLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 操作日志控制器
 */
@Tag(name = "操作日志", description = "操作日志查询、删除、清空、导出接口")
@RestController
@RequestMapping("/monitor/operlog")
public class SysOperLogController {

    private final ISysOperLogService operLogService;

    public SysOperLogController(ISysOperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /**
     * 分页查询操作日志列表
     */
    @Operation(summary = "分页查询操作日志", description = "根据条件分页查询操作日志列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    public Result<IPage<SysOperLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {

        LambdaQueryWrapper<SysOperLog> queryWrapper = new LambdaQueryWrapper<>();

        // 根据条件查询
        if (title != null && !title.isEmpty()) {
            queryWrapper.like(SysOperLog::getTitle, title);
        }
        if (operName != null && !operName.isEmpty()) {
            queryWrapper.like(SysOperLog::getOperName, operName);
        }
        if (businessType != null) {
            queryWrapper.eq(SysOperLog::getBusinessType, businessType);
        }
        if (status != null) {
            queryWrapper.eq(SysOperLog::getStatus, status);
        }
        if (beginTime != null && !beginTime.isEmpty()) {
            queryWrapper.ge(SysOperLog::getOperTime, LocalDateTime.parse(beginTime + "T00:00:00"));
        }
        if (endTime != null && !endTime.isEmpty()) {
            queryWrapper.le(SysOperLog::getOperTime, LocalDateTime.parse(endTime + "T23:59:59"));
        }

        // 按操作时间倒序排列
        queryWrapper.orderByDesc(SysOperLog::getOperTime);

        Page<SysOperLog> page = new Page<>(pageNum, pageSize);
        IPage<SysOperLog> pageResult = operLogService.page(page, queryWrapper);

        return Result.success(pageResult);
    }

    /**
     * 删除操作日志
     */
    @Operation(summary = "删除操作日志", description = "批量删除操作日志")
    @DeleteMapping("/{operIds}")
    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    public Result<Void> remove(@PathVariable Long[] operIds) {
        if (operIds == null || operIds.length == 0) {
            return Result.error("请选择要删除的日志");
        }
        operLogService.removeByIds(Arrays.asList(operIds));
        return Result.success();
    }

    /**
     * 清空操作日志
     */
    @Operation(summary = "清空操作日志", description = "清空所有操作日志")
    @DeleteMapping("/clean")
    @PreAuthorize("@ss.hasPermi('monitor:operlog:clean')")
    public Result<Void> clean() {
        operLogService.cleanOperLog();
        return Result.success();
    }

    /**
     * 导出操作日志
     */
    @Operation(summary = "导出操作日志", description = "导出操作日志到Excel")
    @PostMapping("/export")
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    public void export(HttpServletResponse response,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Integer status) throws IOException {

        LambdaQueryWrapper<SysOperLog> queryWrapper = new LambdaQueryWrapper<>();
        if (title != null && !title.isEmpty()) {
            queryWrapper.like(SysOperLog::getTitle, title);
        }
        if (operName != null && !operName.isEmpty()) {
            queryWrapper.like(SysOperLog::getOperName, operName);
        }
        if (businessType != null) {
            queryWrapper.eq(SysOperLog::getBusinessType, businessType);
        }
        if (status != null) {
            queryWrapper.eq(SysOperLog::getStatus, status);
        }
        queryWrapper.orderByDesc(SysOperLog::getOperTime);

        List<SysOperLog> list = operLogService.list(queryWrapper);

        // 创建Excel工作簿
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("操作日志");

        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = { "日志编号", "系统模块", "操作类型", "操作人员", "IP地址", "IP信息", "操作状态", "操作日期", "耗时(毫秒)" };
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // 填充数据
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < list.size(); i++) {
            SysOperLog log = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(log.getOperId() != null ? log.getOperId() : 0);
            row.createCell(1).setCellValue(log.getTitle() != null ? log.getTitle() : "");
            row.createCell(2).setCellValue(getBusinessTypeName(log.getBusinessType()));
            row.createCell(3).setCellValue(log.getOperName() != null ? log.getOperName() : "");
            row.createCell(4).setCellValue(log.getOperIp() != null ? log.getOperIp() : "");
            row.createCell(5).setCellValue(log.getOperLocation() != null ? log.getOperLocation() : "");
            row.createCell(6).setCellValue(log.getStatus() != null && log.getStatus() == 0 ? "成功" : "失败");
            row.createCell(7).setCellValue(log.getOperTime() != null ? log.getOperTime().format(formatter) : "");
            row.createCell(8).setCellValue(log.getCostTime() != null ? log.getCostTime() : 0);
        }

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("操作日志_" + System.currentTimeMillis() + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 获取业务类型名称
     */
    private String getBusinessTypeName(Integer businessType) {
        if (businessType == null)
            return "其它";
        return switch (businessType) {
            case 1 -> "新增";
            case 2 -> "修改";
            case 3 -> "删除";
            case 4 -> "授权";
            case 5 -> "导出";
            case 6 -> "导入";
            case 7 -> "强退";
            case 8 -> "生成代码";
            case 9 -> "清空数据";
            default -> "其它";
        };
    }
}
