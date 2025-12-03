package petersen.examples.transactions.springtransactions.domain.repository.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    @Override
    public List<UserEntity> findAll() {
        return jpaUserRepository.findAll();
    }

    @Override
    public void save(UserEntity userEntity) {
        jpaUserRepository.save(userEntity);
    }

    @Override
    public void deleteAll() {
        jpaUserRepository.deleteAll();
    }
}
