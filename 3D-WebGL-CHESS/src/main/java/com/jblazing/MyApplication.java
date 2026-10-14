package com.jblazing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;


/**
 * The web app. What @SpringBootApplication would do, but the scan leaves out com.jblazing.engine: that
 * is the chess engine service, a separate app, whose classes are on the classpath in the tests.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.jblazing", excludeFilters = {
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.jblazing\\.engine\\..*")})
public class MyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyApplication.class, args);
	}

}
