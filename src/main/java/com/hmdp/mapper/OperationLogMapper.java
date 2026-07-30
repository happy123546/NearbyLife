package com.hmdp.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.OperationLogs;

public interface OperationLogMapper extends BaseMapper<OperationLogs> {
    void saveOperationLog(OperationLogs opl);
}
