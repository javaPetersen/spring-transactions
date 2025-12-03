package petersen.examples.transactions.springtransactions.domain.locking.optimistic;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryPurchaseService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 50;
    private final PurchaseService purchaseService;

    /**
     * Retry wrapper for optimistic locking.
     */
    public void purchaseWithRetry(Long productId, int quantity) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                purchaseService.purchase(productId, quantity);
                return; // sukces
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Retry {}/{}: wykorzystano wszystkie możliwe próby", attempt, MAX_ATTEMPTS);
                    throw ex; // ostatnia próba — rzucamy dalej
                }

                // logowanie + krótka pauza
                log.error("Retry {}/{}: konflikt wersji, ponawiam...", attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

}
