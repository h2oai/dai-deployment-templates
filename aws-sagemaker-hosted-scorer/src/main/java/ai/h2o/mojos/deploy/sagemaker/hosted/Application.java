package ai.h2o.mojos.deploy.sagemaker.hosted;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {
    @Autowired
    private Serve sv = null;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            sv.init();
            sv.server();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
