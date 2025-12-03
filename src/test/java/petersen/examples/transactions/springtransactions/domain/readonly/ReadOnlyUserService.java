package petersen.examples.transactions.springtransactions.domain.readonly;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserEntity;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadOnlyUserService {

    private final UserRepository userRepository;

    /**
     * Transakcja oznaczona jako readOnly = true.
     *
     * Informuje Springa i warstwę persystencji, że ta operacja ma charakter tylko do odczytu.
     * Spring przekazuje tę informację do menedżera transakcji oraz — w przypadku JPA — do Hibernate,
     * który może pominąć pewne operacje (np. flush) dla lepszej wydajności.
     *
     * flush w Hibernate to operacja synchronizacji stanu encji trzymanych w pamięci z bazą danych — czyli fizyczne
     * wykonanie INSERT, UPDATE, DELETE, które były wcześniej przygotowane, ale jeszcze nie wysłane. W transakcjach
     * oznaczonych jako readOnly = true, Hibernate zwykle pomija flush, co przyspiesza wykonanie operacji odczytu.
     *
     * W przypadku baz danych, takich jak PostgreSQL, JDBC może ustawić transakcję jako "read only"
     * również na poziomie samej bazy — co może skutkować zablokowaniem operacji modyfikujących dane
     * (INSERT, UPDATE, DELETE) i rzuceniem wyjątku.
     *
     * Główne korzyści: optymalizacja, bezpieczeństwo semantyczne, możliwe blokady zapisu po stronie bazy.
     * Uwaga: readOnly nie gwarantuje całkowitej ochrony przed zmianami — zależy to od ORM i bazy.
     */

    @Transactional(readOnly = true)
    public List<UserEntity> readOnlyList() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public void storeReadOnly(UserEntity user) {
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public void deleteReadonly() {
        userRepository.deleteAll();
    }
}
