package petersen.examples.transactions.springtransactions.domain.isolation;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.repository.account.Account;
import petersen.examples.transactions.springtransactions.domain.repository.account.AccountRepository;

import java.util.concurrent.CountDownLatch;

/**
 * POZIOMY IZOLACJI TRANSAKCJI — WPROWADZENIE
 *
 * Poziom izolacji określa, jak i kiedy zmiany dokonane w jednej transakcji stają się widoczne
 * dla innych współbieżnych transakcji. Bezpośrednio wpływa to na spójność danych i równoczesność przetwarzania.
 *
 * Typowe anomalie przy niskiej izolacji:
 * - Dirty Read: odczyt danych zapisanych przez inną transakcję, która jeszcze się nie zatwierdziła.
 * - Non-repeatable Read: ten sam SELECT w obrębie jednej transakcji zwraca różne wyniki.
 * - Phantom Read: pojawiają się nowe wiersze w drugim identycznym zapytaniu SELECT.
 *
 * Poziomy izolacji (od najsłabszego do najmocniejszego):
 * - READ_UNCOMMITTED: pozwala na dirty read.
 * - READ_COMMITTED: blokuje dirty read (domyślny poziom np. w PostgreSQL).
 * - REPEATABLE_READ: blokuje dirty i non-repeatable read.
 * - SERIALIZABLE: blokuje wszystkie anomalie, ale mocno ogranicza współbieżność – transakcje działają jakby były wykonywane jedna po drugiej.
 *
 *
 * Przykład 1: System księgowy / saldo konta
 * Scenariusz:
 * Użytkownik wykonuje dwa przelewy z tego samego konta równocześnie.
 *
 * Każda transakcja najpierw sprawdza saldo, potem odejmuje kwotę i zapisuje nową wartość.
 *
 * Co się może zepsuć:
 * Obie transakcje odczytują np. saldo = 1000
 *
 * Każda zdejmuje 800 → końcowy stan konta: -600 😬
 *
 * Typowa przyczyna:
 * Zbyt słaba izolacja (READ COMMITTED)
 *
 * Brak blokady zapisu
 *
 * Rozwiązania:
 * REPEATABLE_READ lub SERIALIZABLE (blokują lub wykryją konflikt)
 *
 * SELECT ... FOR UPDATE (blokada pesymistyczna)
 *
 * @Version – optymistyczna blokada
 *
 * Przykład 2: Fakturowanie na koniec miesiąca
 * Scenariusz:
 * System tworzy faktury na podstawie listy zakupów z ostatniego miesiąca.
 *
 * Podczas generowania ktoś doda jeszcze jedną transakcję zakupową.
 *
 * Co się może zepsuć:
 * Faktura nie zawiera pełnej sumy → użytkownik zapłaci mniej, firma traci 💸
 *
 * Rozwiązania:
 * REPEATABLE_READ → nie zobaczysz nowych wpisów dodanych po starcie transakcji
 *
 * Albo: zamrozić dane (np. skopiować je do tabeli tymczasowej)
 *
 * Przykład 3: Statystyki – liczba aktywnych użytkowników
 * Scenariusz:
 * Codziennie system liczy ilu użytkowników się zalogowało.
 *
 * Inne transakcje w międzyczasie dodają logi logowania.
 *
 * Co się może zepsuć:
 * Wynik statystyki może być niespójny (zawierać część danych z „przyszłości”)
 *
 * Rozwiązania:
 * READ COMMITTED może wystarczyć
 *
 * REPEATABLE_READ – gdy liczysz więcej niż raz w tej samej transakcji
 *
 * Przykład 4: Koszyk zakupowy – sprawdzenie dostępności
 * Scenariusz:
 * Klient klika „kup teraz”, a system sprawdza czy produkt jeszcze dostępny.
 *
 * W tym czasie inny klient kupuje ostatni egzemplarz.
 *
 * Co się może zepsuć:
 * System potwierdzi zakup, mimo że towaru już nie ma
 *
 * Rozwiązania:
 * SELECT ... FOR UPDATE na stock (blokada)
 *
 * @Version na encji Product
 *
 * lub SERIALIZABLE → ale trzeba radzić sobie z retry po serialization failure
 *
 * Przykład 5: Numeracja dokumentów
 * Scenariusz:
 * Każda nowa faktura dostaje unikalny numer (np. FV-2025-001)
 *
 * Równocześnie uruchamiane są 2 generacje faktur
 *
 * Co się może zepsuć:
 * Obie dostają ten sam numer → constraint violation lub duplikaty
 *
 * Rozwiązania:
 * SERIALIZABLE – wykryje konflikt i zmusi do retry
 * Albo: własny generator numerów z blokadą (np. @Lock(LockModeType.PESSIMISTIC_WRITE))
 *
 * ✍Przykład 6: Głosowanie / ankieta
 * Scenariusz:
 * Użytkownik głosuje w ankiecie
 *
 * System odczytuje bieżącą liczbę głosów i zwiększa o 1
 *
 * Co się może zepsuć:
 * Dwa głosy oddane jednocześnie → jeden nadpisuje drugi → tylko +1 zamiast +2
 *
 * Rozwiązania:
 * @Version na encji z licznikiem głosów
 *
 * UPDATE votes SET count = count + 1 WHERE id = ? – atomiczny update
 * SERIALIZABLE lub SELECT FOR UPDATE – jeśli trzeba więcej logiki
 */
@Service
@RequiredArgsConstructor
public class IsolationService {
    private final AccountRepository accountRepository;
    private final IsolationUpdateService isolationUpdateService;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public int readBalance(Long accountId) {
        return getAccountBalance(accountId);
    }

    @Transactional
    public int readBalanceTwiceWithSync(Long accountId, CountDownLatch latch, CountDownLatch latch2) {
        int first = getAccountBalance(accountId);
        latch.countDown(); // sygnalizujemy: pierwszy SELECT wykonany

        try {
            latch2.await();
            entityManager.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int second = getAccountBalance(accountId);
        return second - first;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public int readBalanceTwiceWithSyncRepeatable(Long accountId, CountDownLatch latch, CountDownLatch latch2) {
        int first = getAccountBalance(accountId);
        latch.countDown(); // sygnalizujemy: pierwszy SELECT wykonany

        try {
            latch2.await();
            entityManager.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int second = getAccountBalance(accountId);
        return second - first;
    }

    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public int readUncommitted(Long accountId) {
        return accountRepository.findById(accountId).map(Account::getBalance).orElse(0);
    }

    @Transactional
    public void updateBalance(Long accountId, int newBalance) {
        accountRepository.findById(accountId).ifPresent(acc -> {
            acc.setBalance(newBalance);
            accountRepository.save(acc);
        });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int countAccountsTwiceWithDelay() {
        long first = accountRepository.count();
        try {
            Thread.sleep(3000); // czas na równoległe wstawienie nowego rekordu
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long second = accountRepository.count();
        return (int) (second - first);
    }


    /**
     * Poziom izolacji SERIALIZABLE — NAJBEZPIECZNIEJSZY, ale NAJMNIEJ WYDAJNY.
     *
     * SERIALIZABLE symuluje, jakby transakcje wykonywały się JEDNA PO DRUGIEJ (sekwencyjnie),
     * nawet jeśli w rzeczywistości są uruchamiane współbieżnie.
     *
     * Blokuje WSZYSTKIE typowe anomalie:
     * - dirty read (odczyt niezatwierdzonych zmian),
     * - non-repeatable read (ta sama encja zwraca różne wartości w jednej transakcji),
     * - phantom read (pojawiły się nowe rekordy między SELECT-ami).
     *
     * W praktyce oznacza to, że:
     * - jedna transakcja może być zablokowana, aż druga się zakończy,
     * - mogą występować wyjątki typu: "could not serialize access due to concurrent update"
     *   → trzeba je łapać i ponawiać operację.
     *
     * SERIALIZABLE zapewnia NAJWYŻSZĄ spójność, ale może znacząco ograniczyć współbieżność.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int countAccountsTwiceWithDelaySerializable() {
        long first = accountRepository.count();
        try {
            Thread.sleep(3000); // czas na równoległe wstawienie nowego rekordu
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long second = accountRepository.count();
        return (int) (second - first);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertNewAccount(String owner, int balance) {
        Account acc = new Account();
        acc.setOwner(owner);
        acc.setBalance(balance);
        accountRepository.save(acc);
    }

    private Integer getAccountBalance(Long accountId) {
        return accountRepository.findById(accountId).map(Account::getBalance).orElseThrow(() -> new IllegalStateException("#getAccountBalance -> Could not find data for account with id: [%s]".formatted(accountId)));
    }
}

