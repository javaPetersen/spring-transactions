package petersen.examples.transactions.springtransactions.config;

import org.testcontainers.containers.PostgreSQLContainer;

public class SharedPostgreSQLContainer extends PostgreSQLContainer<SharedPostgreSQLContainer> {

    private static final String IMAGE_VERSION = "postgres:17.5-alpine";
    private static SharedPostgreSQLContainer container;

    private SharedPostgreSQLContainer() {
        super(IMAGE_VERSION);
    }

    public static SharedPostgreSQLContainer getInstance() {
        if (container == null) {
            container = new SharedPostgreSQLContainer();
        }
        return container;
    }
}
