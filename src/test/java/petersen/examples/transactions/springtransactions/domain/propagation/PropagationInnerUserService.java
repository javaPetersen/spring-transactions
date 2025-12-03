package petersen.examples.transactions.springtransactions.domain.propagation;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserEntity;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserRepository;

/**
 * Propagation określa, jak zachować się, gdy metoda oznaczona @Transactional
 * zostaje wywołana w kontekście istniejącej już transakcji.
 *
 * Wartości propagation decydują:
 * - czy dołączyć się do istniejącej transakcji
 * - czy zawiesić ją i rozpocząć nową
 * - czy w ogóle nie uruchamiać transakcji
 *
 * Wpływa to na:
 * - zasięg rollbacku,
 * - izolację zapisów,
 * - czas commitowania.
 */
@Service
@RequiredArgsConstructor
public class PropagationInnerUserService {
    private final UserRepository repository;

    /** Propagation.REQUIRED
     * Domyślna strategia propagacji transakcji w Springu.
     *
     * Jeśli w momencie wywołania metody już istnieje transakcja — metoda zostanie w niej uruchomiona.
     * Jeśli nie — zostanie utworzona nowa transakcja.
     *
     * Efekt:
     * - Metoda dziedziczy zakres transakcji wywołującej.
     * - Rollback w metodzie zewnętrznej cofa również zmiany w tej metodzie.
     * - Dobre dla spójnych operacji składających się z wielu kroków.
     *
     * Przykład: outerService → innerService
     * Gdy inner rzuca wyjątek, outer też się cofa — bo to jedna transakcja.
     *
     * REQUIRED – domyślne, spójne operacje biznesowe
     * Przykład: Rejestracja użytkownika
     * Metoda registerUser():
     *
     * zapisuje dane użytkownika,
     * tworzy domyślne ustawienia,
     * wysyła powitalny wpis do logu (jeśli też w bazie).
     *
     * Dlaczego REQUIRED?
     * Całość powinna być spójna – jeśli którakolwiek z operacji się wywali, żadne dane nie powinny zostać zapisane.
     */

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveRequired(String name) {
        repository.save(new UserEntity(name));
    }

    /** Propagation.REQUIRES_NEW
     * Wymusza utworzenie nowej, niezależnej transakcji.
     *
     * Gdy metoda zostanie wywołana z istniejącej transakcji:
     * - ta transakcja zostaje zawieszona,
     * - uruchamiana jest nowa transakcja tylko dla tej metody.
     *
     * Efekt:
     * - Metoda działa całkowicie niezależnie.
     * - Jej rollback nie wpływa na metodę nadrzędną i odwrotnie.
     * - Nadaje się np. do logowania błędów lub wysyłania maili, które muszą się wykonać niezależnie.
     *
     * Przykład: outer zapisuje A, inner zapisuje B (REQUIRES_NEW),
     * outer rzuca wyjątek → A się cofa, B zostaje.
     *
     * REQUIRES_NEW – niezależne logowanie / audyt
     * Przykład: Logowanie błędów podczas przetwarzania faktury
     * Metoda processInvoice() zapisuje dane, ale:
     *
     * gdy pojawi się wyjątek, wywołuje logError() z REQUIRES_NEW.
     *
     * Dlaczego REQUIRES_NEW?
     * Log błędu musi zostać zapisany, nawet jeśli główna operacja zostanie wycofana.
     * To zapewnia, że informacja o błędzie nie zniknie przez rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRequiresNew(String name) {
        repository.save(new UserEntity(name));
    }


    /** Propagation.NESTED
     * Tworzy zagnieżdżoną transakcję w postaci savepointu — o ile główna transakcja już istnieje.
     *
     * Działa tylko, gdy:
     * - istnieje aktywna transakcja nadrzędna,
     * - baza danych wspiera mechanizm savepointów (np. PostgreSQL, nie domyślnie H2).
     *
     * Efekt:
     * - Rollback w metodzie wewnętrznej (inner) nie musi powodować rollbacku metody zewnętrznej (outer).
     * - Można lokalnie cofnąć część operacji, ale kontynuować nadrzędną transakcję.
     * - Nadaje się np. do walidacji, operacji eksperymentalnych lub warunkowych.
     *
     * Przykład: outer zapisuje A, inner zapisuje B i rzuca wyjątek,
     * inner się cofa, ale A z outera zostaje.
     *
     * NESTED – częściowe wycofanie, savepointy
     * Przykład: Przetwarzanie wielu plików w jednej operacji
     * Metoda processAllFiles() iteruje po plikach i dla każdego wywołuje processSingleFile().
     *
     * Główna transakcja REQUIRED
     *
     * Wewnątrz processSingleFile() – NESTED
     *
     * Dlaczego NESTED?
     * Błąd w jednym pliku powoduje rollback tylko jego zmian – cała transakcja nie zostaje wycofana.
     * Idealne do dużych batchów lub integracji z zewnętrznymi danymi.
     *
     *
     * BEGIN;
     *
     * INSERT INTO orders VALUES (1, 'Order A');
     *
     * SAVEPOINT after_order;
     *
     * INSERT INTO discounts VALUES (1, 'INVALID'); -- np. rzuca błąd
     *
     * ROLLBACK TO SAVEPOINT after_order;
     *
     * COMMIT;
     */
    @Transactional(propagation = Propagation.NESTED)
    public void saveNestedAndFail(String name) {
        repository.save(new UserEntity(name));
        throw new RuntimeException("Fail in nested");
    }


    /** Propagation.NOT_SUPPORTED
     * Wyłącza transakcję — metoda działa bez transakcji, nawet jeśli została wywołana z poziomu innej, aktywnej transakcji.
     *
     * Jeśli istnieje transakcja — zostaje tymczasowo zawieszona.
     *
     * Efekt:
     * - Zmiany w tej metodzie nie są częścią transakcji wywołującej.
     * - Jej działania nie zostaną wycofane przy rollbacku metody nadrzędnej.
     * - Nadaje się do działań, które muszą się wykonać niezależnie (np. szybkie SELECTY, niebuforowane operacje, logowanie).
     *
     * Przykład: outer zapisuje A (w transakcji), inner zapisuje B (bez transakcji),
     * outer rzuca wyjątek → A się cofa, B zostaje.
     *
     * NOT_SUPPORTED – wyłączenie transakcji, np. do logiki cache/cache miss
     * Przykład: Sprawdzenie dostępności zasobu w pamięci lub zewnętrznym API
     * Metoda checkAvailability() nie potrzebuje transakcji – odczyt danych, może być z cache lub API.
     *
     * Dlaczego NOT_SUPPORTED?
     * Nie chcemy przypadkiem obejmować takich operacji transakcją — mógłby się utworzyć niepotrzebny kontekst i blokady.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void saveNoTx(String name) {
        repository.save(new UserEntity(name));
    }


    /** Propagation.NEVER
     * Zakazuje działania w transakcji.
     *
     * Jeśli metoda zostanie wywołana w kontekście aktywnej transakcji — zostanie rzucony wyjątek IllegalTransactionStateException.
     *
     * Efekt:
     * - Gwarantuje brak transakcji — zabezpiecza operacje, które muszą się wykonać "goło", np. natywne działania JDBC.
     *
     * Przykład: outer ma transakcję, inner (NEVER) — wyjątek, bo nie może działać w transakcji.
     *
     * NEVER – bezpieczeństwo przed przypadkowym uruchomieniem w transakcji
     * Przykład: Operacja administracyjna, która nie może działać w kontekście transakcji
     * Np. natywna modyfikacja struktury bazy (zmiana sekwencji, migracja).
     *
     * Dlaczego NEVER?
     * Gwarantujemy, że nie zostanie przypadkiem uruchomiona w ramach rollbackowalnej transakcji.
     * Bezpiecznik.
     */
    @Transactional(propagation = Propagation.NEVER)
    public void failIfTxPresent(String name) {
        repository.save(new UserEntity(name));
    }

    /** Propagation.MANDATORY
     * Wymaga, by metoda została wywołana w ramach istniejącej transakcji.
     *
     * Jeśli nie ma aktywnej transakcji — zostaje rzucony wyjątek IllegalTransactionStateException.
     *
     * Efekt:
     * - Zapewnia, że operacja jest zawsze częścią większej transakcji.
     * - Dobre do metod, które nie mają sensu poza kontekstem nadrzędnej transakcji (np. walidacja stanu lub spójność danych).
     *
     * Przykład: outer() bez @Transactional wywołuje inner() z MANDATORY → wyjątek.
     *
     * MANDATORY – wymuszenie działania w kontekście większej transakcji
     * Przykład: Operacja pomocnicza wykonywana tylko jako część większego procesu
     * Np. metoda recalculateBalance() — powinna być wywoływana tylko w ramach pełnego closeAccount().
     *
     * Dlaczego MANDATORY?
     * Chcemy, by to była część większej operacji — nigdy samodzielna.
     * Brak transakcji = wyjątek → ochrona integralności procesu.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMandatory(String name) {
        repository.save(new UserEntity(name));
    }


    /** Propagation.SUPPORTS
     * Jeśli istnieje transakcja — metoda zostanie w niej uruchomiona.
     * Jeśli nie — metoda wykona się bez transakcji.
     *
     * Efekt:
     * - Elastyczne zachowanie — może działać z lub bez transakcji.
     * - Dobre do operacji opcjonalnych, gdzie transakcja jest mile widziana, ale niekonieczna.
     *
     * Przykład: outer() z @Transactional → inner działa transakcyjnie.
     * outer() bez @Transactional → inner działa nietransakcyjnie.
     *
     * SUPPORTS – opcjonalna transakcyjność
     * Przykład: Pobranie danych do raportu
     * Metoda getSalesSummary() działa:
     * w transakcji, jeśli jest,bez niej — też OK.
     *
     * Dlaczego SUPPORTS?
     * Chcemy, żeby była elastyczna – np. używana z serwisu raportowego lub z UI, gdzie kontekst transakcji może się różnić.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void saveSupports(String name) {
        repository.save(new UserEntity(name));
    }

}
