package com.aurora.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan("com.aurora")
@MapperScan("com.aurora.**.mapper")
@EnableAsync
@EnableScheduling
public class AuroraApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuroraApplication.class, args);
    }

    @Bean
    public ApplicationRunner printFooter() {
        return args -> {
            System.out.println("\n" + "\n" +
                    "==========================================================\n" +
                    "   ___   _   _  ____    ___   ____      _   \n" +
                    "  / _ \\ | | | ||  _ \\  / _ \\ |  _ \\    / \\  \n" +
                    " | |_| || | | || |_) || | | || |_) |  / _ \\ \n" +
                    " |  _  || |_| ||  _ < | |_| ||  _ <  / ___ \\\n" +
                    " |_| |_| \\___/ |_| \\_\\ \\___/ |_| \\_\\/_/   \\_\\\n" +
                    "\n" +
                    " :: 极光已点亮，系统启动成功！\n" +
                    " :: 祝你好运，开发者！\n" +
                    "==========================================================\n"

            );
        };
    }

}