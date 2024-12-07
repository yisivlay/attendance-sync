package com.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AttendanceSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(AttendanceSyncApplication.class, args);
	}

}
