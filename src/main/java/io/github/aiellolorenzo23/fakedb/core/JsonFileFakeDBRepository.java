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
        this.objectMapper = objectMapper;
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
        return readTable().stream()
                .map(item -> objectMapper.convertValue(item, entityClass))
                .toList();
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

        FakeDBDatabase database = store.load();
        List<Object> rows = database.table(schema, table);
        int existingIndex = findIndexById(rows, id);
        Object value = objectMapper.convertValue(entity, Object.class);
        if (existingIndex >= 0) {
            rows.set(existingIndex, value);
        } else {
            rows.add(value);
        }
        store.save(database);
        return entity;
    }

    @Override
    public synchronized List<T> saveAll(Collection<T> entities) {
        List<T> saved = new ArrayList<>();
        for (T entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public synchronized boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public synchronized long count() {
        return readTable().size();
    }

    @Override
    public synchronized void deleteById(ID id) {
        FakeDBDatabase database = store.load();
        List<Object> rows = database.table(schema, table);
        boolean removed = rows.removeIf(item -> Objects.equals(readId(objectMapper.convertValue(item, entityClass)), id));
        if (!removed) {
            throw new FakeDBEntityNotFoundException("No FakeDB entity found with id " + id);
        }
        store.save(database);
    }

    @Override
    public synchronized void delete(T entity) {
        deleteById(readId(entity));
    }

    @Override
    public synchronized void deleteAll() {
        FakeDBDatabase database = store.load();
        database.table(schema, table).clear();
        store.save(database);
    }

    private List<Object> readTable() {
        return store.load().table(schema, table);
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
