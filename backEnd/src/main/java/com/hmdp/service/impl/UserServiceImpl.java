package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

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
        session.setAttribute("code",code);
//        s3:发送验证码
        log.debug("发送短信验证码成功,验证码:{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        s1:校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
//          不符合,返回错误信息

            return Result.fail("手机号格式错误!");
        }
//        s2:校验验证码
        Object cachecode = session.getAttribute("code");
        String code =  loginForm.getCode();
//        s2.1:不一致,报错
        if(cachecode == null || !cachecode.equals(code)){
            return Result.fail("验证码错误!");
        }
//        s2.2:一致,根据手机号查询用户
//        User user = new User().setPhone(phone);
//        userMapper.getUserByPhone(user);
//        s3:数据库中用户是否存在
        User user = query().eq("phone", phone).one();
        if(user == null){
//        s3.1:不存在,创建用户,写入数据库
            user = createUserWithPhone(phone);
        }
//        s3.2:存在.

//        保存用户信息到 session
        session.setAttribute("user",user);
        return Result.ok();//基于 Session 登录,无需返回任何东西
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
