package petersen.examples.transactions.springtransactions.domain.locking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private int stock;

    @Version
    private int version;

    public Product() {}

    public Product(String name, int stock) {
        this.name = name;
        this.stock = stock;
    }

}
