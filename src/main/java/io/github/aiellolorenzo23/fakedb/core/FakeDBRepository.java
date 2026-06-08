package io.github.aiellolorenzo23.fakedb.core;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public interface FakeDBRepository<T, ID> {

    List<T> findAll();

    List<T> findAll(Predicate<T> predicate);

    Optional<T> findFirst(Predicate<T> predicate);

    Optional<T> findById(ID id);

    T save(T entity);

    List<T> saveAll(Collection<T> entities);

    boolean existsById(ID id);

    long count();

    void deleteById(ID id);

    void delete(T entity);

    void deleteAll();
}
