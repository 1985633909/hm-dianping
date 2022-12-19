package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author: 19856
 * @date: 2022/12/3-19:12
 * @description: 访问需要用户登录的页面时的拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要拦截
        if (UserHolder.getUser() == null){
            //没有需要拦截
            response.setStatus(401);
            return false;
        }
        //由用户则放行
        return true;
    }


}
