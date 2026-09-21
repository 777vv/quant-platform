package com.quant.system.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.quant.common.result.R;
import com.quant.system.dto.ProfileRequest;
import com.quant.system.dto.UserVO;
import com.quant.system.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口（FR4）。
 * 注意：类上不要加 @SaIgnore——会把资料读写全部放开为匿名访问；
 * 需要免鉴权的接口（如外部联调的测试端点）只在方法上加 @SaIgnore。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 查询当前用户资料 */
    @GetMapping("/profile")
    public R<UserVO> profile() {
        return R.ok(userService.profile());
    }

    /** 维护昵称/通知邮箱 */
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody ProfileRequest request) {
        userService.updateProfile(request);
        return R.ok();
    }

    @SaIgnore
    @GetMapping("/test2")
    public R<RoomDeviceDTO> test2(){
        return R.ok(RoomDeviceDTO.initTestData());
    }
}
