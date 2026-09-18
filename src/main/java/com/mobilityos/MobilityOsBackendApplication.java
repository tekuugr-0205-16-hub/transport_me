



package com.mobilityos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MobilityOsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				MobilityOsBackendApplication.class,
				args
		);
	}
}

