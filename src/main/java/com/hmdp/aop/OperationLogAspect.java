package com.hmdp.aop;


import com.hmdp.service.IOperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Aspect
public class OperationLogAspect{
    @Autowired
    private IOperationLogService iOperationLogService;

    //aop切面记录操作日志
    @Around("@annotation(com.hmdp.anno.OperationLog)")
    public Object operationLog(ProceedingJoinPoint JointPoint){
        //记录初始使时间
        long begin = System.currentTimeMillis();
        Object result = null;
        String error = null;

        try {
            //运行目标方法
            result = JointPoint.proceed();
        } catch (Throwable e) {
            error = e.toString();
            throw new RuntimeException(e);
        } finally {
            //异步日志保存
            iOperationLogService.saveOperationLog(JointPoint, error, begin);
        }
        return result;
    }


}
