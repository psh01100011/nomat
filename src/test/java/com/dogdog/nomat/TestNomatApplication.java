package com.dogdog.nomat;

import org.springframework.boot.SpringApplication;

public class TestNomatApplication {

	public static void main(String[] args) {
		SpringApplication.from(NomatApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
