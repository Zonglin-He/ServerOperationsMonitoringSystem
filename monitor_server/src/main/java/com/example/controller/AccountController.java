package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.ChangePasswordVO;
import com.example.entity.vo.request.ModifyEmailVO;
import com.example.entity.vo.response.AccountVO;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.ControllerUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api/user")
public class AccountController {

    @Resource
    AccountService service;

    @Resource
    ControllerUtils utils;

    @GetMapping("/info")
    public RestBean<AccountVO> info(@RequestAttribute(Const.ATTR_USER_ID)  int id) {
        Account account = service.findAccountById(id);
        return RestBean.success(account.asViewObject(AccountVO.class));
    }



    @PostMapping("/modify-email")
    public RestBean<Void> modifyEmail(@RequestAttribute(Const.ATTR_USER_ID)  int id,
                                      @RequestBody @Valid ModifyEmailVO vo) {
        String result = service.modifyEmail(id, vo);
        return result == null ? RestBean.success() : RestBean.failure(400, result);
    }

    @PostMapping("/change-password")
    public RestBean<Void> changePassword(@RequestAttribute(Const.ATTR_USER_ID) int id,
                                         @RequestBody @Valid ChangePasswordVO vo) {
        return this.messageHandler(() -> service.changePassword(id, vo));
    }

    private <T> RestBean<T> messageHandler(Supplier<String> action) {
        String message = action.get();
        if (message == null) {return RestBean.success();}
        else {return RestBean.failure(400, message);}
    }


}
