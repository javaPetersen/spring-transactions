package petersen.examples.transactions.springtransactions.domain.repository.user;

import java.util.List;

public interface UserRepository {
    List<UserEntity> findAll();

    void save(UserEntity userEntity);

    void deleteAll();
}
