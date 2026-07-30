package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

//    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //token有效期更新

        //获取请求头的token
        String token = request.getHeader("authorization");


        //token为空
        if(StrUtil.isBlank(token)){
            return true;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;
        //根据token结合的id从redis获取用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //判断用户是否为空
        if(userMap.isEmpty()){
            return true;
        }

        //将map转换为userDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //将用户存入ThreadLocal中
        UserHolder.saveUser(userDTO);

        //刷新key有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
