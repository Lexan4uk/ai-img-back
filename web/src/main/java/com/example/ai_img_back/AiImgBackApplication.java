package com.example.ai_img_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiImgBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiImgBackApplication.class, args);
	}

}
