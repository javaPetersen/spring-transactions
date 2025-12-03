package petersen.examples.transactions.springtransactions.domain.isolation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.repository.account.AccountRepository;

@Service
@RequiredArgsConstructor
public class IsolationUpdateService {
    private final AccountRepository accountRepository;

    @Transactional(label = "IsolationUpdateServiceTransaction", propagation = Propagation.REQUIRES_NEW)
    public void forceUpdate(Long id, int newValue) {
        accountRepository.findById(id).ifPresent(a -> {
            a.setBalance(newValue);
            accountRepository.save(a);
        });
    }
}
