package io.github.jys0615.stayport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StayportApplication {

	public static void main(String[] args) {
		SpringApplication.run(StayportApplication.class, args);
	}

}
