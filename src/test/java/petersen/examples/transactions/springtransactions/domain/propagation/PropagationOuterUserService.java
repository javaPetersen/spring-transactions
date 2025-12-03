package petersen.examples.transactions.springtransactions.domain.propagation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserEntity;
import petersen.examples.transactions.springtransactions.domain.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class PropagationOuterUserService {

    private final UserRepository repository;
    private final PropagationInnerUserService inner;

    @Transactional
    public void outerWithInnerRequired_thenFail(String outerName, String innerName) {
        repository.save(new UserEntity(outerName));
        inner.saveRequired(innerName);
        throw new RuntimeException("Fail outer");
    }

    @Transactional
    public void outerWithInnerRequiresNew_thenFail(String outerName, String innerName) {
        repository.save(new UserEntity(outerName));
        try {
            inner.saveRequiresNew(innerName);
        } catch (Exception ignored) {}
        throw new RuntimeException("Fail outer");
    }

    @Transactional
    public void outerWithInnerNestedCatch(String outerName, String innerName) {
        repository.save(new UserEntity(outerName));
        try {
            inner.saveNestedAndFail(innerName);
        } catch (Exception ignored) {}
    }

    @Transactional
    public void outerWithSupports(String outerName, String innerName) {
        repository.save(new UserEntity(outerName));
        inner.saveSupports(innerName);
    }

    public void outerWithoutTransactionCallingMandatory(String innerName) {
        inner.saveMandatory(innerName);
    }

    public void outerWithoutTransactionCallingSupports(String innerName) {
        inner.saveSupports(innerName);
    }

    @Transactional
    public void outerWithNeverShouldFail(String name) {
        inner.failIfTxPresent(name);
    }

    public void outerWithoutTransactionCallingNever(String name) {
        inner.failIfTxPresent(name);
    }

    public void outerWithoutTransactionCallingNotSupported(String name) {
        inner.saveNoTx(name);
    }
}
