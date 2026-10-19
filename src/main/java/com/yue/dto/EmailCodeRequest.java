package com.yue.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailCodeRequest(
        @NotBlank
        @Size(max = 254)
        @Email(message = "邮箱格式不正确")
        String email) {
}
