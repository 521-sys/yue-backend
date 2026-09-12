package com.yue.service;

import com.yue.dto.AuthResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import com.yue.security.JwtUtil;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试：注册/登录/重复用户名/密码错误。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET =
            "unit-test-secret-key-0123456789abcdef0123456789abcdef-0123456789abcdef";

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder encoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, encoder, new JwtUtil(SECRET, 3600000));
    }

    @Test
    @DisplayName("注册成功：密码 BCrypt 加密存储，返回 token")
    void register_success() {
        when(userRepo.existsByUsername("tom")).thenReturn(false);
        when(encoder.encode("123456")).thenReturn("$2a$hash");
        // 模拟 JPA save 分配主键的行为
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse resp = authService.register("tom", "123456");

        assertEquals("tom", resp.username());
        assertNotNull(resp.token());
        assertNotNull(resp.userId());
        verify(userRepo).save(argThat(u -> "$2a$hash".equals(u.getPasswordHash())));
        verify(encoder).encode("123456");
    }

    @Test
    @DisplayName("注册失败：用户名已存在")
    void register_duplicateUsername_throws() {
        when(userRepo.existsByUsername("tom")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.register("tom", "123456"));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("登录成功：密码匹配返回 token")
    void login_success() {
        User user = User.builder().id(7L).username("tom").passwordHash("$2a$hash").build();
        when(userRepo.findByUsername("tom")).thenReturn(Optional.of(user));
        when(encoder.matches("123456", "$2a$hash")).thenReturn(true);

        AuthResponse resp = authService.login("tom", "123456");

        assertEquals("tom", resp.username());
        assertEquals(7L, resp.userId());
        assertNotNull(resp.token());
    }

    @Test
    @DisplayName("登录失败：密码错误")
    void login_wrongPassword_throws() {
        User user = User.builder().id(7L).username("tom").passwordHash("$2a$hash").build();
        when(userRepo.findByUsername("tom")).thenReturn(Optional.of(user));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.login("tom", "wrong"));
    }

    @Test
    @DisplayName("登录失败：用户不存在")
    void login_unknownUser_throws() {
        when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.login("nobody", "123456"));
    }
}
