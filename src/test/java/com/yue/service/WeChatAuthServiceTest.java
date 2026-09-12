package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import com.yue.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WeChatAuthService 单元测试：开发模式 code→openid 映射、首次注册、重复登录复用账号、非法 code。
 * 真实微信 jscode2session 接口在集成测试里再测，此处验证服务本身的业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class WeChatAuthServiceTest {

    private static final String SECRET =
            "unit-test-secret-key-0123456789abcdef0123456789abcdef-0123456789abcdef";

    @Mock
    private UserRepository userRepo;
    @Mock
    private PasswordEncoder encoder;

    private WeChatAuthService wxService;

    @BeforeEach
    void setUp() {
        // 不配置 appid/secret → 开发模式，按 code 生成 openid = dev_openid_<code>
        wxService = new WeChatAuthService(userRepo, encoder, new JwtUtil(SECRET, 3600000), new ObjectMapper(), "", "");
    }

    @Test
    @DisplayName("开发模式下 isProductionConfigured 返回 false")
    void devMode_configuredIsFalse() {
        assertFalse(wxService.isProductionConfigured());
    }

    @Test
    @DisplayName("首次 code 登录：创建用户，写入 wxOpenid")
    void firstLogin_createsUser() {
        when(userRepo.findByWxOpenid("dev_openid_code123")).thenReturn(Optional.empty());
        when(userRepo.existsByUsername(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse r = wxService.loginByCode("code123");

        assertNotNull(r.token());
        assertTrue(r.username().startsWith("wx_"));
        assertEquals(1L, r.userId());
        verify(userRepo).save(argThat(u -> "dev_openid_code123".equals(u.getWxOpenid())));
    }

    @Test
    @DisplayName("再次用同一个 code：复用已有账号")
    void secondLogin_reusesExistingUser() {
        User existing = User.builder()
                .id(55L).username("wx_abcdef").passwordHash("$2a$old")
                .wxOpenid("dev_openid_code99").build();
        when(userRepo.findByWxOpenid("dev_openid_code99")).thenReturn(Optional.of(existing));

        AuthResponse r = wxService.loginByCode("code99");

        assertEquals(55L, r.userId());
        assertEquals("wx_abcdef", r.username());
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("空 / 空白 code 拒绝")
    void emptyCode_throws() {
        assertThrows(IllegalArgumentException.class, () -> wxService.loginByCode(""));
        assertThrows(IllegalArgumentException.class, () -> wxService.loginByCode("   "));
        assertThrows(IllegalArgumentException.class, () -> wxService.loginByCode(null));
    }

    @Test
    @DisplayName("用户名冲突时追加后缀：不中断登录")
    void usernameConflict_suffixes() {
        when(userRepo.findByWxOpenid("dev_openid_suffix")).thenReturn(Optional.empty());
        // 基础名 "wx_suffix" 前 99 次都被占用，最终回退到时间戳路径
        when(userRepo.existsByUsername("wx_suffix")).thenReturn(true);
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        AuthResponse r = wxService.loginByCode("suffix");
        assertEquals(7L, r.userId());
        verify(userRepo).save(any());
    }
}
