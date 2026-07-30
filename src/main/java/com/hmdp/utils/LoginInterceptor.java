package com.hmdp.utils;


import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Component
public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //检查用户是否存在，即是否登录
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }

        return true;
    }
}
