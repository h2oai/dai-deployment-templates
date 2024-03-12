package ai.h2o.mojos.deploy.local.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ScorerApplication {
  public static void main(String[] args) {
    SpringApplication.run(ScorerApplication.class, args);
  }
}
