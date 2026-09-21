package com.yue.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新请求：昵称与预设头像均可选（只更新非空字段）。
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = 16, message = "昵称最长 16 个字符")
    private String nickname;

    @Size(max = 32, message = "头像标识过长")
    private String avatar;
}
