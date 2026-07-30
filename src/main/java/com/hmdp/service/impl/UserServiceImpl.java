package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.intern.InternUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.anno.OperationLog;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @OperationLog(desc = "发送验证码")
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号码格式错误");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //session.setAttribute("code", code);
        log.debug("验证码{}", code);
        return Result.ok();
    }

    @OperationLog(desc = "用户登录或注册")
    @Override
    public Result login(LoginFormDTO loginForm) {
        //获取手机号,验证码,密码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();

        User user = new User();
        //判断该用户为验证码登录还是密码登录

        //验证码登录
        if(password == null || password.isEmpty()){
            user = codeLogin(phone, code);
        }else if(code == null || code.isEmpty()){
            //密码登录
            user = passwordLogin(phone, password);
        }else{
            throw new RuntimeException("请输入验证码或密码");
        }


        //生成token
        String token = UUID.randomUUID().toString();

        //拷贝用户信息（除去隐私信息）
        UserDTO userDto = BeanUtil.copyProperties(user, UserDTO.class);
        //转换为Map
        Map<String, Object> userMap = BeanUtil.beanToMap(userDto);
        Object id = userMap.get("id");
        String sid = id.toString();
        userMap.put("id", sid);

        //存入redis
        String loginUserKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(loginUserKey, userMap);
        //设置有效期
        stringRedisTemplate.expire(loginUserKey, LOGIN_USER_TTL, TimeUnit.MINUTES);


        System.out.println("!!!!!token" + token);
        return Result.ok(token);
    }

    public User codeLogin (String phone, String code){
        //从redis获取验证码  cache缓存
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //判断验证码是否一致
        //String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            throw new RuntimeException("验证码错误");
        }

        //判断用户是否存在
        User user = query().eq("phone", phone).one();
        //否，则创建用户
        if(user == null){
            user = createUserWithPhone(phone);
        }

        return user;
    }

    public User passwordLogin (String phone, String password){
        //获取登录失败次数
        String failCountStr = stringRedisTemplate.opsForValue().get(LOGIN_FAIL_KEY + phone);

        int failCount = 0;
        //判断是否>=5次,是则直接返回
        if(failCountStr != null){
            //转为int类型
            failCount = Integer.parseInt(failCountStr);
            if(failCount >= 5){
                Long expire = stringRedisTemplate.getExpire(LOGIN_FAIL_KEY + phone, TimeUnit.SECONDS);
                log.info("登录失败{}次，请在{}秒后再重试登录", failCount, expire);
                throw new RuntimeException("登录失败，请5分钟后在重试");
            }
        }

        //判断手机号及密码是否存在且正确
        User user = query()
                .eq("phone", phone)
                .eq("password", password)
                .one();

        //不存在则在缓存添加失败次数，次数达到五次则直接返回
        if(user == null){
            stringRedisTemplate.opsForValue().increment(LOGIN_FAIL_KEY + phone);
            stringRedisTemplate.expire(LOGIN_FAIL_KEY + phone, LOGIN_FAIL_TTL, TimeUnit.MINUTES);


            //增加失败次数
            //if(failCountStr == null) {
                //stringRedisTemplate.opsForValue().set(LOGIN_FAIL_KEY + phone, "1", LOGIN_FAIL_TTL, TimeUnit.MINUTES);
            //}else{
                //方案2
                //stringRedisTemplate.opsForValue().increment(LOGIN_FAIL_KEY + phone);

                //方案1
                //failCountStr = stringRedisTemplate.opsForValue().get(LOGIN_FAIL_KEY + phone);
                //failCount = Integer.parseInt(failCountStr);
                //failCount++;
                //stringRedisTemplate.opsForValue().set(LOGIN_FAIL_KEY + phone, Integer.toString(failCount), LOGIN_FAIL_TTL, TimeUnit.MINUTES);
            //}
            log.info("手机号{}或密码{}错误", phone, password);
            throw new RuntimeException("手机号或密码错误！请重新输入");
        }
        
        //都正确，则删除失败计数
        stringRedisTemplate.delete(LOGIN_FAIL_KEY + phone);
        return user;
    }

    public User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        //log.debug("有注册新用户{}--- {}---{}", user.getNickName(), user.getPhone(), user.getId());
        return user;
    }
}
