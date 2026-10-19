package com.yue.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailLoginRequest(
        @NotBlank
        @Size(max = 254)
        @Email(message = "邮箱格式不正确")
        String email,
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
        String code) {
}
