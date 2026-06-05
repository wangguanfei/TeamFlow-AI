package com.teamflow.ai;

import com.teamflow.ai.common.security.CorsProperties;
import com.teamflow.ai.common.security.JwtProperties;
import com.teamflow.ai.modules.ai.provider.AiProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@MapperScan("com.teamflow.ai.modules.*.mapper")
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AiProperties.class})
public class TeamFlowAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamFlowAiApplication.class, args);
    }
}
