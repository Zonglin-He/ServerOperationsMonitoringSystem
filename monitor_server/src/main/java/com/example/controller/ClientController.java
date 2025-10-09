package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Client;
import com.example.entity.vo.request.ClientDetailVO;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.service.ClientService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/monitor")
public class ClientController {

    @Resource
    ClientService service;

    @GetMapping("/register")
    public RestBean<Void> registerClient(@RequestHeader("Authorization") String token) {
        return service.verifyAndRegister(token) ?
                RestBean.success() : RestBean.failure(401, "Invalid token");
    }

    @PostMapping("/detail")
    public RestBean<Void> updateClientDetail(@RequestAttribute(Const.ATTR_CLIENT)Client client,
                                             @RequestBody @Valid ClientDetailVO vo){
        service.updateClientDetail(vo, client);
        return RestBean.success();
    }

    @PostMapping("/runtime")
    public RestBean<Void> updateClientRuntime(@RequestAttribute(Const.ATTR_CLIENT)Client client,
                                             @RequestBody RuntimeDetailVO vo){
        service.updateRuntimeDetail(vo, client);
        return RestBean.success();
                                             }
}
