package petersen.examples.transactions.springtransactions.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class TestContainerConfig {
    private static final SharedPostgreSQLContainer CONTAINER = SharedPostgreSQLContainer.getInstance();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        CONTAINER.start();
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }
}