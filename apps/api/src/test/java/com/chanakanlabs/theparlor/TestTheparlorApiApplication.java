package com.chanakanlabs.theparlor;

import org.springframework.boot.SpringApplication;

public class TestTheparlorApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(TheparlorApiApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
