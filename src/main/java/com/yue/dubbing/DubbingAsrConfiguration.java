package com.yue.dubbing;

import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.*;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.time.*;

@Configuration
public class DubbingAsrConfiguration {
    @Bean TencentAsrGateway dubbingGateway(DubbingAsrProperties properties) {
        if (properties.isEnabled() && (properties.getSecretId().isBlank() || properties.getSecretKey().isBlank()))
            throw new IllegalStateException("腾讯云识别已启用，但未配置服务端密钥");
        var http = new HttpProfile(); http.setConnTimeout(15); http.setReadTimeout(30); http.setWriteTimeout(30);
        http.setHttpClient(new okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(15)).readTimeout(java.time.Duration.ofSeconds(30))
            .writeTimeout(java.time.Duration.ofSeconds(30)).callTimeout(java.time.Duration.ofSeconds(45)).build());
        var profile = new ClientProfile(); profile.setHttpProfile(http);
        return new TencentAsrGateway(new AsrClient(new Credential(properties.getSecretId(), properties.getSecretKey()), "", profile));
    }
    @Bean AsrAudioExtractor dubbingExtractor(DubbingAsrProperties properties) { return new AsrAudioExtractor(properties); }
    @Bean AsrBudget dubbingBudget(DubbingAsrProperties properties) {
        return new AsrBudget(Path.of(properties.getBudgetFile()), properties.getMonthlySeconds(), Clock.system(ZoneId.of("Asia/Shanghai")));
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<DubbingUploadFilter> dubbingUploadFilter(DubbingAsrProperties properties, DubbingTranscriptionService service) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new DubbingUploadFilter(properties, service));
        registration.setOrder(0);
        return registration;
    }
}
