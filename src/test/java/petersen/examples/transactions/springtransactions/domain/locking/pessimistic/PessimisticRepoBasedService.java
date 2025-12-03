package petersen.examples.transactions.springtransactions.domain.locking.pessimistic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.locking.Product;
import petersen.examples.transactions.springtransactions.domain.locking.ProductRepository;

@Service
@RequiredArgsConstructor
public class PessimisticRepoBasedService {

    private final ProductRepository repo;

    @Transactional
    public void safePurchase(Long id, int qty) {
        Product product = repo.findByIdForUpdate(id).orElseThrow();

        if (product.getStock() < qty) {
            throw new IllegalStateException("Za mało w magazynie");
        }

        product.setStock(product.getStock() - qty);

        // zostaje w transakcji — blokada aktywna do końca
        try {
            Thread.sleep(500); // symulacja długiego przetwarzania
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
