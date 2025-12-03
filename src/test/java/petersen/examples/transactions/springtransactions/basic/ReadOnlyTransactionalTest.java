package petersen.examples.transactions.springtransactions.basic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.JpaSystemException;
import petersen.examples.transactions.springtransactions.config.TestContainerConfig;
import petersen.examples.transactions.springtransactions.domain.repository.user.JpaUserRepository;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserEntity;
import petersen.examples.transactions.springtransactions.domain.readonly.ReadOnlyUserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ReadOnlyTransactionalTest extends TestContainerConfig {

    @Autowired
    private ReadOnlyUserService readOnlyUserService;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @BeforeEach
    void setUp() {
        UserEntity userEntity = new UserEntity("super-user");
        jpaUserRepository.save(userEntity);
    }

    @AfterEach
    void tearDown() {
        jpaUserRepository.deleteAll();
    }

    @Test
    @DisplayName("Should allow to read data in read-only mode")
    void shouldAllowToReadDataWhileOnReadOnlyMode() {
        //given
        final int expectedSize = 1;
        //when
        List<UserEntity> userEntities = readOnlyUserService.readOnlyList();
        //then
        assertThat(userEntities).hasSize(expectedSize);
    }

    @Test
    @DisplayName("Should throw an exception if readonly transaction tries to insert data")
    void shouldNotAllowToInsertDataWhileOnReadOnlyMode() {
        //given
        UserEntity userEntity = new UserEntity();
        //when
        //then
        assertThrows(JpaSystemException.class, () -> readOnlyUserService.storeReadOnly(userEntity));
    }

    @Test
    @DisplayName("Should not modify existing data while on read only mode")
    void shouldNotModifyExistingEntityWhileOnReadOnlyMode() {
        //given
        final String username = "test";
        UserEntity saved = jpaUserRepository.save(new UserEntity());
        saved.setUsername(username);

        //when
        readOnlyUserService.storeReadOnly(saved);

        //then
        UserEntity modified = jpaUserRepository.findById(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Entity was not found!"));
        assertThat(modified.getUsername()).isNotEqualTo(username);
        assertThat(modified.getUsername()).isNull();
    }

    @Test
    @DisplayName("Should not delete any data while on read only mode")
    void shouldNotDeleteAnyDataWhileOnReadOnlyMode() {
        //given
        final int expectedSize = 1;
        //when
        readOnlyUserService.deleteReadonly();
        //then
        List<UserEntity> userEntities =
                readOnlyUserService.readOnlyList();
        assertThat(userEntities).hasSize(expectedSize);
    }



}
