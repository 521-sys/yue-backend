package com.yue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 粤语学习后端启动入口。
 *
 * 启动方式（IntelliJ IDEA）：右键运行本类，或用 IDEA 内置 Maven 执行 spring-boot:run。
 * 默认端口 8080，前端通过 http://localhost:8080 访问。
 */
@SpringBootApplication
public class YueApplication {

    public static void main(String[] args) {
        SpringApplication.run(YueApplication.class, args);
    }
}
