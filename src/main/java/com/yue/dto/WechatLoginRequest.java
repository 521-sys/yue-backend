package com.yue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信小程序登录请求体：前端通过 wx.login() 拿到的临时 code
 */
@Data
public class WechatLoginRequest {

    @NotBlank(message = "code 不能为空")
    @Size(max = 256, message = "code 过长")
    private String code;
}
