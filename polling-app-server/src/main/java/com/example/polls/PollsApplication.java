package com.example.polls;

import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;

import com.example.polls.security.JwtTokenProvider;

@SpringBootApplication
@EntityScan(basePackageClasses = {
        PollsApplication.class,
        Jsr310JpaConverters.class
})
@EnableConfigurationProperties(JwtTokenProvider.class)
public class PollsApplication {

    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(final String[] args) {
        SpringApplication.run(PollsApplication.class, args);
    }
}
