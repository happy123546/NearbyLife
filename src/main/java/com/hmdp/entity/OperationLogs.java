package com.hmdp.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("operation_log")
public class OperationLogs {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String operationDesc;
    private String methodName;
    private Long costTime;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
}
