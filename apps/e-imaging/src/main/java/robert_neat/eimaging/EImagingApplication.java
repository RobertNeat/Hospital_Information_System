package robert_neat.eimaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EImagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EImagingApplication.class, args);
    }

}
