package com.yue.service;

import com.yue.dto.ProfileResponse;
import com.yue.entity.User;
import com.yue.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProfileService 单元测试：资料查询（手机号脱敏）、昵称/头像更新。
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepo;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userRepo);
    }

    private User sampleUser() {
        return User.builder().id(1L).username("user5678").phone("13812345678")
                .nickname("用户5678").avatar(null).build();
    }

    @Test
    @DisplayName("查询资料：手机号脱敏返回")
    void getProfile_masksPhone() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sampleUser()));

        ProfileResponse resp = profileService.getProfile(1L);

        assertEquals("138****5678", resp.phone());
        assertEquals("用户5678", resp.nickname());
        assertNull(resp.avatar());
    }

    @Test
    @DisplayName("更新资料：同时更新昵称与头像")
    void updateProfile_both() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse resp = profileService.updateProfile(1L, "小明", "🦊");

        assertEquals("小明", resp.nickname());
        assertEquals("🦊", resp.avatar());
        verify(userRepo).save(argThat(u -> "小明".equals(u.getNickname()) && "🦊".equals(u.getAvatar())));
    }

    @Test
    @DisplayName("更新资料：只更新头像，昵称保持原样")
    void updateProfile_avatarOnly() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sampleUser()));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse resp = profileService.updateProfile(1L, null, "🐼");

        assertEquals("用户5678", resp.nickname());
        assertEquals("🐼", resp.avatar());
    }

    @Test
    @DisplayName("更新资料：昵称超长拒绝")
    void updateProfile_nicknameTooLong_throws() {
        when(userRepo.findById(1L)).thenReturn(Optional.of(sampleUser()));
        String longNick = "很长的昵称".repeat(5); // 20 字 > 16

        assertThrows(IllegalArgumentException.class,
                () -> profileService.updateProfile(1L, longNick, null));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("用户不存在：抛异常")
    void getProfile_unknownUser_throws() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> profileService.getProfile(99L));
    }
}
