package petersen.examples.transactions.springtransactions.basic.locking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import petersen.examples.transactions.springtransactions.config.TestContainerConfig;
import petersen.examples.transactions.springtransactions.domain.locking.Product;
import petersen.examples.transactions.springtransactions.domain.locking.ProductRepository;
import petersen.examples.transactions.springtransactions.domain.locking.pessimistic.PessimisticRepoBasedService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PessimisticLockingTest extends TestContainerConfig {

    @Autowired
    private PessimisticRepoBasedService service;
    @Autowired
    private ProductRepository repo;

    @BeforeEach
    void setup() {
        repo.deleteAll();
        repo.save(new Product("Pessimistic", 10));
    }

    @Test
    void shouldBlockSecondTransactionUntilFirstCommits() throws InterruptedException {
        Long id = repo.findAll().getFirst().getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> log = Collections.synchronizedList(new ArrayList<>());

        Runnable t1 = () -> {
            try {
                log.add("T1: start");
                service.safePurchase(id, 5);
                log.add("T1: end");
            } finally {
                latch.countDown();
            }
        };

        Runnable t2 = () -> {
            try {
                Thread.sleep(100); // ruszy po T1
                log.add("T2: start");
                service.safePurchase(id, 5);
                log.add("T2: end");
            } catch (Exception e) {
                log.add("T2: exception: " + e.getClass().getSimpleName());
            } finally {
                latch.countDown();
            }
        };

        new Thread(t1).start();
        new Thread(t2).start();
        latch.await();

        log.forEach(System.out::println);

        // jeśli działa blokada: T2:end dopiero po T1:end
        assertThat(log.indexOf("T2: end")).isGreaterThan(log.indexOf("T1: end"));
    }
}
