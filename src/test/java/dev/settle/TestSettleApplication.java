package dev.settle;

import org.springframework.boot.SpringApplication;

public class TestSettleApplication {

	public static void main(String[] args) {
		SpringApplication.from(SettleApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
