package com.doopp.gauss.api.controller;

import com.doopp.gauss.api.entity.UserEntity;
import com.doopp.gauss.api.entity.dto.SessionKeyDTO;
import com.doopp.gauss.api.entity.dto.UserMeDTO;
import com.doopp.gauss.api.service.AccountService;
import com.doopp.gauss.api.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 登录界面
 *
 * Created by henry on 2017/10/19.
 */
@Controller
@RequestMapping(value = "api/v1/")
public class AccountController {

    @Autowired
    private AccountService accountService;

    /**
     * 用户登录，获取 access token
     * @param account 用户的账号
     * @param password 用户的密码
     * @return accessToken
     * @throws Exception 账号或密码错误
     */
    @ResponseBody
    @RequestMapping(value = "login", method = RequestMethod.POST)
    public SessionKeyDTO login(@RequestParam("account") String account, @RequestParam("password") String password) throws Exception {
        // GET user
        UserEntity user = accountService.getUserOnLogin(account, password);
        String accessToken = accountService.registerSession(user);
        return new SessionKeyDTO(accessToken);
    }

    /**
     * 用户注册，获取 access token
     * @param account 用户的账号
     * @param password 用户的密码
     * @return accessToken
     * @throws Exception 账号已经存在，或密码不合格
     */
    @ResponseBody
    @RequestMapping(value = "register", method = RequestMethod.POST)
    public SessionKeyDTO register(@RequestParam("account") String account,
                                  @RequestParam("password") String password,
                                  @RequestParam("nickName") String nickName) throws Exception {

        UserEntity user = accountService.getUserOnRegister(account, password, nickName);
        String accessToken = accountService.registerSession(user);
        return new SessionKeyDTO(accessToken);
    }

    /**
     * 查询当前用户信息
     *
     * @param sessionUser 当前用户
     * @return 返回用户信息
     */
    @ResponseBody
    @RequestMapping(value = "/user/me", method = RequestMethod.GET)
    public UserMeDTO getMe(@RequestAttribute("sessionUser") UserEntity sessionUser) {
        // GET user
        return CommonUtils.modelMap(sessionUser, UserMeDTO.class);
    }

    /**
     * 更新当前用户信息
     *
     * @param sessionUser 当前用户
     * @return 返回用户信息
     */
    @ResponseBody
    @RequestMapping(value = "/user/me", method = RequestMethod.PUT)
    public UserMeDTO updateMe(@RequestAttribute("sessionUser") UserEntity sessionUser) {
        // GET user
        return CommonUtils.modelMap(sessionUser, UserMeDTO.class);
    }
}
