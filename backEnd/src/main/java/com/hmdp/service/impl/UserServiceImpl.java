package com.hmdp.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;

import cn.hutool.core.bean.BeanUtil;
//import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
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
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private StringRedisTemplate stringRedisTemplate;
    /**
     * 用户登录
     * @param phone
     * @param session
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
//        s1:校验手机号

        if(RegexUtils.isPhoneInvalid(phone)){
//        s1.1:不符合,返回错误信息

            return Result.fail("手机号格式错误!");
        }

//        s1.2:符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
//        s2:保存验证码到 session
//        session.setAttribute("code",code);
//        保存验证码到 redis 当中
        // 业务前缀(提前定义好常量)+手机号作为 key,验证码为值
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        s3:发送验证码
        log.debug("发送短信验证码成功,验证码:{}",code);
        return Result.ok();
    }

    /**
     * 短信登录和注册
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        s1:校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
//          不符合,返回错误信息

            return Result.fail("手机号格式错误!");
        }
//        s2:校验验证码
//        Object cachecode = session.getAttribute("code");
        String code =  loginForm.getCode();
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        s2.1:不一致,报错
        if(cachecode == null || !cachecode.equals(code)){
            return Result.fail("验证码错误!");
        }
//        s2.2:一致,根据手机号查询用户
        User user = query().eq("phone", phone).one();

//        s3:数据库中用户是否存在
        if(user == null){
//        s3.1:不存在,创建用户,写入数据库
            user = createUserWithPhone(phone);
        }
//        s3.2:存在.

//        保存用户信息到 session
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user,userDTO);
//        session.setAttribute("user",userDTO);
//        return Result.ok();//基于 Session 登录,无需返回任何东西
//        TODO: session 逻辑转换为 redis 逻辑
//        s4: 保存信息 到 redis

//        随机生成 Token 作为登录令牌
        String token = UUID.randomUUID().toString(true);
//        将 User 独享转换为 hash 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//        存储
//        设置有效期,避免内存占用过多(参考 session的有效期(30min)
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userMap);
//        先存值,才能设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
//        30分钟完全不操作才能删除,所以一旦访问,需要更新 expire 时间
//        返回对象(token)
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
