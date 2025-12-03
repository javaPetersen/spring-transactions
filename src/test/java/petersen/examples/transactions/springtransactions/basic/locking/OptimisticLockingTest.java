package petersen.examples.transactions.springtransactions.basic.locking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import petersen.examples.transactions.springtransactions.config.TestContainerConfig;
import petersen.examples.transactions.springtransactions.domain.locking.Product;
import petersen.examples.transactions.springtransactions.domain.locking.ProductRepository;
import petersen.examples.transactions.springtransactions.domain.locking.optimistic.PurchaseService;
import petersen.examples.transactions.springtransactions.domain.locking.optimistic.RetryPurchaseService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 🔐 Optimistic Locking (optymistyczne blokowanie)
 *
 * Mechanizm kontroli współbieżności, który zakłada brak konfliktów podczas modyfikacji danych.
 *
 * Działa na poziomie aplikacji — każda encja zawiera specjalne pole wersji (@Version),
 * które jest automatycznie inkrementowane przy każdej modyfikacji.
 *
 * Podczas zapisu Hibernate generuje SQL typu:
 *   UPDATE table SET ..., version = version + 1 WHERE id = ? AND version = ?
 *
 * Jeśli wersja rekordu w bazie różni się od tej z encji (czyli ktoś inny go zmodyfikował),
 * to żaden wiersz nie zostaje zaktualizowany, a JPA rzuca wyjątek:
 *   javax.persistence.OptimisticLockException
 *
 * 🔧 Wymagania:
 * - Encja musi mieć pole oznaczone adnotacją @Version (np. int, long, Timestamp)
 * - Działa w każdej relacyjnej bazie danych (PostgreSQL, MySQL, Oracle, MSSQL)
 *
 * ✅ Zastosowania:
 * - Dane rzadko edytowane współbieżnie
 * - Aplikacje webowe z małą szansą na konflikt zapisu
 * - Systemy, gdzie zależy nam na maksymalnej wydajności i braku blokad
 *
 * ❌ Ograniczenia:
 * - Konflikty wykrywane dopiero przy zapisie (commit/flush)
 * - Wymaga obsługi retry przy równoczesnych modyfikacjach
 *
 * 📌 W Spring Data działa automatycznie przy encjach z @Version,
 *     nie trzeba używać żadnych specjalnych adnotacji w repository
 */
@SpringBootTest
class OptimisticLockingTest extends TestContainerConfig {

    @Autowired
    private ProductRepository repo;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private RetryPurchaseService retryPurchaseService;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        repo.save(new Product("Gitarra", 10));
    }

    @Test
    void testOptimisticLocking_conflictOccurs() throws Exception {
        //given
        Long productId = repo.findAll().getFirst().getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        Runnable task1 = () -> performPurchase(productId, exceptions, latch);
        Runnable task2 = () -> performPurchase(productId, exceptions, latch);

        //when
        pool.submit(task1);
        pool.submit(task2);
        latch.await();

        //then
        // tylko jedna transakcja powinna się udać
        Product updated = getProduct(productId);
        assertThat(updated.getStock()).withFailMessage("Stock powinien zmniejszyć się tylko raz").isEqualTo(4);

        // jedna z transakcji musi rzucić ObjectOptimisticLockingFailureException
        assertThat(exceptions)
                .hasSize(1)
                .anySatisfy(e -> assertThat(e).isInstanceOf(ObjectOptimisticLockingFailureException.class));
    }

    @Test
    @DisplayName("Happy path! Should allow sequential updates!")
    void optimisticLocking_shouldAllowSequentialUpdates() {
        //given
        Long productId = repo.save(new Product("Sequential", 10)).getId();

        //when
        purchaseService.purchase(productId, 4); // stock = 6
        purchaseService.purchase(productId, 2); // stock = 4

        //then
        Product result = getProduct(productId);
        assertThat(result.getStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("Version should be incremented!")
    void optimisticLocking_shouldIncrementVersion() {
        //given
        final int expectedVersion = 1;
        Long productId = repo.save(new Product("Version Test", 10)).getId();

        //when
        purchaseService.purchase(productId, 2); // stock = 4

        //then
        Product product = getProduct(productId);
        assertThat(product.getVersion()).isEqualTo(expectedVersion);
    }

    @Test
    void shouldRetryPurchaseWhenConflictOccurs() throws InterruptedException {
        Long productId = repo.save(new Product("Retry Gitarra", 10)).getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        Runnable t1 = () -> performRetryablePurchase(productId, 6, exceptions, latch);
        Runnable t2 = () -> performRetryablePurchase(productId, 4, exceptions, latch);

        Executors.newFixedThreadPool(2).submit(t1);
        Executors.newFixedThreadPool(2).submit(t2);
        latch.await();

        Product result = getProduct(productId);
        assertThat(result.getStock()).isZero();

        // co najwyżej jedna transakcja mogła się nie udać
        assertThat(exceptions).hasSizeLessThanOrEqualTo(1);
    }

    private Product getProduct(Long productId) {
        return repo.findById(productId).orElseThrow(() -> new IllegalStateException("Should not happened!"));
    }

    private void performPurchase(Long productId, List<Exception> exceptions, CountDownLatch latch) {
        try {
            purchaseService.purchase(productId, 6);
        } catch (Exception e) {
            exceptions.add(e);
        } finally {
            latch.countDown();
        }
    }

    private void performRetryablePurchase(Long productId, int qty, List<Exception> exceptions, CountDownLatch latch) {
        try {
            retryPurchaseService.purchaseWithRetry(productId, qty);
        } catch (Exception e) {
            exceptions.add(e);
        } finally {
            latch.countDown();
        }
    }
}
