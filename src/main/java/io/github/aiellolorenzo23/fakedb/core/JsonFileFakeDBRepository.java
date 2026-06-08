package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBEntityNotFoundException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class JsonFileFakeDBRepository<T, ID> implements FakeDBRepository<T, ID> {

    private final ObjectMapper objectMapper;
    private final FakeDBStore store;
    private final String schema;
    private final String table;
    private final Class<T> entityClass;
    private final Field idField;

    private final Class<ID> idClass;

    public JsonFileFakeDBRepository(
            FakeDBStore store,
            ObjectMapper objectMapper,
            String schema,
            String table,
            Class<T> entityClass,
            Class<ID> idClass
    ) {
        this.objectMapper = FakeDBObjectMapper.configure(objectMapper.copy());
        this.store = store;
        this.schema = schema;
        this.table = table;
        this.entityClass = entityClass;
        this.idClass = idClass;
        this.idField = resolveIdField(entityClass);
        this.idField.setAccessible(true);
    }

    @Override
    public synchronized List<T> findAll() {
        return store.read(database -> toEntities(database.table(schema, table)));
    }

    @Override
    public synchronized List<T> findAll(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate cannot be null");

        return findAll().stream()
                .filter(predicate)
                .toList();
    }

    @Override
    public synchronized Optional<T> findFirst(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate cannot be null");

        return findAll().stream()
                .filter(predicate)
                .findFirst();
    }

    @Override
    public synchronized Optional<T> findById(ID id) {
        return findAll().stream()
                .filter(entity -> Objects.equals(readId(entity), id))
                .findFirst();
    }

    @Override
    public synchronized T save(T entity) {
        ID id = readId(entity);
        if (id == null) {
            throw new FakeDBConfigurationException("FakeDB entity id cannot be null");
        }

        return store.update(database -> {
            upsert(database.table(schema, table), entity, id);
            return entity;
        });
    }

    @Override
    public synchronized List<T> saveAll(Collection<T> entities) {
        Objects.requireNonNull(entities, "entities cannot be null");

        return store.update(database -> {
            List<Object> rows = database.table(schema, table);
            List<T> saved = new ArrayList<>();

            for (T entity : entities) {
                ID id = readId(entity);
                if (id == null) {
                    throw new FakeDBConfigurationException("FakeDB entity id cannot be null");
                }

                upsert(rows, entity, id);
                saved.add(entity);
            }

            return saved;
        });
    }

    @Override
    public synchronized boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public synchronized long count() {
        return store.read(database -> (long) database.table(schema, table).size());
    }

    @Override
    public synchronized void deleteById(ID id) {
        store.update(database -> {
            List<Object> rows = database.table(schema, table);
            boolean removed = rows.removeIf(item -> Objects.equals(readId(objectMapper.convertValue(item, entityClass)), id));
            if (!removed) {
                throw new FakeDBEntityNotFoundException("No FakeDB entity found with id " + id);
            }
            return null;
        });
    }

    @Override
    public synchronized void delete(T entity) {
        deleteById(readId(entity));
    }

    @Override
    public synchronized void deleteAll() {
        store.update(database -> {
            database.table(schema, table).clear();
            return null;
        });
    }

    private List<T> toEntities(List<Object> rows) {
        return rows.stream()
                .map(item -> objectMapper.convertValue(item, entityClass))
                .toList();
    }

    private void upsert(List<Object> rows, T entity, ID id) {
        int existingIndex = findIndexById(rows, id);
        Object value = objectMapper.convertValue(entity, Object.class);
        if (existingIndex >= 0) {
            rows.set(existingIndex, value);
        } else {
            rows.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private ID readId(T entity) {
        try {
            return (ID) idField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new FakeDBConfigurationException("Cannot read FakeDB id field " + idField.getName(), ex);
        }
    }

    private int findIndexById(List<Object> rows, ID id) {
        for (int i = 0; i < rows.size(); i++) {
            T current = objectMapper.convertValue(rows.get(i), entityClass);
            if (Objects.equals(readId(current), id)) {
                return i;
            }
        }
        return -1;
    }

    private static Field resolveIdField(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(FakeDBId.class)) {
                return field;
            }
        }
        try {
            return entityClass.getDeclaredField("id");
        } catch (NoSuchFieldException ex) {
            throw new FakeDBConfigurationException(
                    "FakeDB entity " + entityClass.getName() + " must have an @FakeDBId field or a field named id",
                    ex
            );
        }
    }
}
