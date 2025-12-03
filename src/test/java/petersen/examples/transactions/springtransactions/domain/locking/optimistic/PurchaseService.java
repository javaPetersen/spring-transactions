package petersen.examples.transactions.springtransactions.domain.locking.optimistic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.locking.Product;
import petersen.examples.transactions.springtransactions.domain.locking.ProductRepository;

@Service
public class PurchaseService {

    @Autowired
    private ProductRepository repo;

    @Transactional
    public void purchase(Long productId, int quantity) {
        Product product = repo.findById(productId).orElseThrow(() ->
                new IllegalArgumentException("Produkt nie istnieje"));

        if (product.getStock() < quantity) {
            throw new IllegalStateException("Za mało w magazynie");
        }

        product.setStock(product.getStock() - quantity);
    }

}
