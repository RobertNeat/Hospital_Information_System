package robert_neat.ereceipt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EReceiptApplication {

    public static void main(String[] args) {
        SpringApplication.run(EReceiptApplication.class, args);
    }

}
