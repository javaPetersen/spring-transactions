package petersen.examples.transactions.springtransactions.basic.isolation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import petersen.examples.transactions.springtransactions.config.TestContainerConfig;
import petersen.examples.transactions.springtransactions.domain.isolation.IsolationService;
import petersen.examples.transactions.springtransactions.domain.isolation.IsolationUpdateService;
import petersen.examples.transactions.springtransactions.domain.repository.account.Account;
import petersen.examples.transactions.springtransactions.domain.repository.account.AccountRepository;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IsolationTransactionalTest extends TestContainerConfig {

    @Autowired
    private IsolationService isolationService;

    @Autowired
    private IsolationUpdateService isolationUpdateService;

    @Autowired
    private AccountRepository accountRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
        isolationService.insertNewAccount("user1", 100);
    }

    @Test
    @DisplayName("READ_COMMITTED: brak dirty read — zmiana nie jest widoczna przed commit")
    void testNoDirtyRead() throws Exception {
        Account acc = accountRepository.findAll().getFirst();

        Future<Integer> readFuture = executor.submit(() -> isolationService.readBalance(acc.getId()));

        isolationService.updateBalance(acc.getId(), 999); // brak commit — pozostaje w tej samej transakcji

        int readBalance = readFuture.get(2, TimeUnit.SECONDS);
        assertThat(readBalance).isEqualTo(100); // nadal widoczna stara wartość
    }

    @Test
    @DisplayName("READ_COMMITTED: non-repeatable read — ta sama encja może mieć inną wartość")
    void testNonRepeatableRead_ReadCommitted() throws Exception {
        Account acc = accountRepository.findAll().getFirst();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        Future<?> update = executor.submit(() -> {
            try {
                latch.await();
                isolationUpdateService.forceUpdate(acc.getId(), 200);
                latch2.countDown();
            } catch (InterruptedException ignored) {}
        });

        int difference = isolationService.readBalanceTwiceWithSync(acc.getId(), latch, latch2);
        update.get();

        assertThat(difference).isEqualTo(100); // wartość powinna się zmienić między odczytami
    }

    @Test
    @DisplayName("REPEATABLE_READ: blokuje non-repeatable read — dwa odczyty dają tę samą wartość")
    void testRepeatableRead_PreventsChangeBetweenReads() throws Exception {
        Account acc = accountRepository.findAll().getFirst();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        Future<?> update = executor.submit(() -> {
            try {
                latch.await();
                isolationUpdateService.forceUpdate(acc.getId(), 200);
                latch2.countDown();
            } catch (InterruptedException ignored) {}
        });

        int difference = isolationService.readBalanceTwiceWithSyncRepeatable(acc.getId(), latch, latch2);
        update.get();

        assertThat(difference).isZero(); // nie powinno być różnicy
    }


    @Test
    @DisplayName("READ_UNCOMMITTED: teoretycznie pozwala na dirty read, ale PostgreSQL nie wspiera — nadal widzimy starą wartość")
    void testReadUncommitted_NoDirtyReadInPostgres() throws Exception {
        Account acc = accountRepository.findAll().getFirst();

        Future<Integer> readFuture = executor.submit(() -> isolationService.readUncommitted(acc.getId()));

        isolationUpdateService.forceUpdate(acc.getId(), 999); // REQUIRES_NEW

        int readBalance = readFuture.get(2, TimeUnit.SECONDS);
        assertThat(readBalance).isEqualTo(100); // PostgreSQL i tak nie pokaże dirty read
    }

    @Test
    @DisplayName("SERIALIZABLE: blokuje phantom read — nowy rekord nie powinien być widoczny")
    void testSerializableBlocksPhantomRead() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Future<Integer> counter = executor.submit(() -> {
            latch.countDown();
            return isolationService.countAccountsTwiceWithDelaySerializable();
        });

        latch.await();
        isolationService.insertNewAccount("phantom", 200);

        int difference = counter.get(5, TimeUnit.SECONDS);

        assertThat(difference).isZero(); // SERIALIZABLE → blokuje phantom read
    }

    @Test
    @DisplayName("READ_COMMITTED: pozwala na phantom read — SELECT może zobaczyć nowy rekord")
    void testReadCommittedAllowsPhantomRead() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Future<Integer> counter = executor.submit(() -> {
            latch.countDown();
            return isolationService.countAccountsTwiceWithDelay();
        });

        latch.await();
        isolationService.insertNewAccount("phantom", 200);

        int difference = counter.get(5, TimeUnit.SECONDS);

        assertThat(difference).isEqualTo(1); // READ_COMMITTED → phantom read możliwy
    }

}
