package io.github.aiellolorenzo23.fakedb.core;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FakeDBRepository<T, ID> {

    List<T> findAll();

    Optional<T> findById(ID id);

    T save(T entity);

    List<T> saveAll(Collection<T> entities);

    boolean existsById(ID id);

    long count();

    void deleteById(ID id);

    void delete(T entity);

    void deleteAll();
}
