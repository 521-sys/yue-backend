package com.yue.dubbing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "app.dubbing-asr")
public class DubbingAsrProperties {
    private boolean enabled = false;
    private String secretId = "";
    private String secretKey = "";
    private String ffmpeg = "ffmpeg";
    private String ffprobe = "ffprobe";
    private String budgetFile = "./data/dubbing-asr-budget.txt";
    private long monthlySeconds = 600;
}
