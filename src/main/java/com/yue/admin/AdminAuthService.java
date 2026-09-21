package com.yue.admin;
import com.yue.security.JwtUtil; import org.springframework.beans.factory.annotation.Value; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service;
@Service public class AdminAuthService { private final PasswordEncoder encoder; private final JwtUtil jwt; @Value("${admin.username:admin}") String username; @Value("${admin.password-hash:}") String passwordHash; public AdminAuthService(PasswordEncoder e, JwtUtil j){encoder=e;jwt=j;} public AdminLoginResponse login(String u,String p){if(!username.equals(u)||passwordHash.isBlank()||!encoder.matches(p,passwordHash))throw new IllegalArgumentException("管理员账号或密码错误");return new AdminLoginResponse(jwt.generateAdmin(username),username);} }

