package com.jblazing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource(value = "file:///etc/jblazing/chess.properties", ignoreResourceNotFound = true)
public class PropertyConfig {

}
