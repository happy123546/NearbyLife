package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.anno.OperationLog;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.OperationLogs;
import com.hmdp.mapper.OperationLogMapper;
import com.hmdp.service.IOperationLogService;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLogs> implements IOperationLogService {

    @Async
    public void saveOperationLog(ProceedingJoinPoint JointPoint, String error, Long begin){
        long end = System.currentTimeMillis();
        //强转为方法签名
        MethodSignature signature = (MethodSignature) JointPoint.getSignature();
        //获取方法名
        Method method = signature.getMethod();
        //获取自定义标签的内容
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        //存入操作日志
        OperationLogs opl = new OperationLogs();
        opl.setOperationDesc(annotation.desc());
        opl.setCostTime(end - begin);
        opl.setCreateTime(LocalDateTime.now());

        opl.setMethodName(method.getName());

        //判断是否抛异常
        if(error != null){
            opl.setStatus(0);
            opl.setErrorMsg(error);
        }else{
            opl.setStatus(1);
        }
        //获取用户id
        UserDTO user = UserHolder.getUser();
        if(user != null){
            Long userId = user.getId();
            opl.setUserId(userId);
        }

        save(opl);

    }

}
