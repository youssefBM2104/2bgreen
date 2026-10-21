package com.twobgreen.gridintelligence;

import org.springframework.boot.SpringApplication;

public class TestGridIntelligenceApplication {

	public static void main(String[] args) {
		SpringApplication.from(GridIntelligenceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
