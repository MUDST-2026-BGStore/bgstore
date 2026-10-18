package com.chanakanlabs.theparlor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(systemName = "The Parlor", sharedModules = "shared")
public class TheparlorApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(TheparlorApiApplication.class, args);
  }
}
