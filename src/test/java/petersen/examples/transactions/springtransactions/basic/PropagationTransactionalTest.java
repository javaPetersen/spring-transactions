package petersen.examples.transactions.springtransactions.basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import petersen.examples.transactions.springtransactions.config.TestContainerConfig;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserEntity;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserRepository;
import petersen.examples.transactions.springtransactions.domain.propagation.PropagationOuterUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropagationTransactionalTest extends TestContainerConfig {

    @Autowired
    PropagationOuterUserService outer;
    @Autowired
    UserRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("REQUIRED: inner i outer dzielą tę samą transakcję — wyjątek w outer powoduje rollback obu")
    void testRequiredRollbackTogether() {
        assertThatThrownBy(() -> outer.outerWithInnerRequired_thenFail("A", "B"))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("REQUIRES_NEW: inner zapisuje niezależnie — outer rzuca wyjątek, ale inner commit")
    void testRequiresNewCommitsDespiteOuterFailing() {
        assertThatThrownBy(() -> outer.outerWithInnerRequiresNew_thenFail("A", "B"))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactly("B");
    }

    @Test
    @DisplayName("NESTED: inner się wycofuje po wyjątku, outer commit")
    void testNestedRollbacksInnerOnly() {
        outer.outerWithInnerNestedCatch("A", "B");
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactly("A");
    }

    @Test
    @DisplayName("SUPPORTS: z transakcją działa w jej kontekście")
    void testSupportsWithTransaction() {
        outer.outerWithSupports("A", "B");
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("SUPPORTS: bez transakcji również działa — bez rollbacku")
    void testSupportsWithoutTransaction() {
        outer.outerWithoutTransactionCallingSupports("A");
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactly("A");
    }

    @Test
    @DisplayName("MANDATORY: brak aktywnej transakcji skutkuje wyjątkiem")
    void testMandatoryWithoutTransactionFails() {
        assertThatThrownBy(() -> outer.outerWithoutTransactionCallingMandatory("A"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("NEVER: metoda wywołana w transakcji rzuca wyjątek")
    void testNeverWithTransactionFails() {
        assertThatThrownBy(() -> outer.outerWithNeverShouldFail("A"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("NEVER: metoda działa poprawnie bez transakcji")
    void testNeverWithoutTransactionSucceeds() {
        outer.outerWithoutTransactionCallingNever("A");
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactly("A");
    }

    @Test
    @DisplayName("NOT_SUPPORTED: działa bez transakcji — nawet jeśli outer nie ma transakcji")
    void testNotSupportedWithoutTransactionSucceeds() {
        outer.outerWithoutTransactionCallingNotSupported("A");
        assertThat(repository.findAll()).extracting(UserEntity::getUsername).containsExactly("A");
    }
}
