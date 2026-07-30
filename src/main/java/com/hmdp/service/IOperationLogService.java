package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.OperationLogs;
import org.aspectj.lang.ProceedingJoinPoint;

public interface IOperationLogService extends IService<OperationLogs> {
    void saveOperationLog(ProceedingJoinPoint JointPoint, String error, Long begin);
}
