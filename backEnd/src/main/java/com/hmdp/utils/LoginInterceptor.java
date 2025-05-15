package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        s1:获取 session
        HttpSession session = request.getSession();
//        s2:获取 session 中的用户
        Object user = session.getAttribute("user");
//        s3:判断用户是否存在
        if(user == null) {
//        s4: 不存在,拦截
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);//SC_UNAUTHORIZED 未授权 401
            return false;
        }
//        s5: 存在,保存在 ThreadLocal里
        UserHolder.saveUser((UserDTO) user);

        return true;// 放行
    }


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
