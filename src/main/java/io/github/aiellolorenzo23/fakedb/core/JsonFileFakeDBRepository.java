package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBCreatedDate;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBGeneratedValue;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBLastModifiedDate;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBNotNull;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBReference;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBUnique;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConstraintViolationException;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBEntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
        return findAll(FakeDBFetchMode.RAW);
    }

    @Override
    public synchronized List<T> findAll(FakeDBFetchMode fetchMode) {
        Objects.requireNonNull(fetchMode, "fetchMode cannot be null");

        return store.read(database -> {
            List<T> entities = toEntities(database.table(schema, table));
            if (fetchMode == FakeDBFetchMode.RESOLVE_REFERENCES) {
                resolveReferences(entities, database);
            }
            return entities;
        });
    }

    @Override
    public synchronized List<T> findAll(Sort sort) {
        Objects.requireNonNull(sort, "sort cannot be null");

        List<T> entities = new ArrayList<>(findAll());
        if (sort.isUnsorted()) {
            return List.copyOf(entities);
        }

        entities.sort(comparator(sort));
        return List.copyOf(entities);
    }

    @Override
    public synchronized Page<T> findAll(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable cannot be null");

        List<T> entities = pageable.getSort().isSorted() ? findAll(pageable.getSort()) : findAll();
        if (pageable.isUnpaged()) {
            return new PageImpl<>(entities);
        }

        int total = entities.size();
        int fromIndex = Math.min((int) pageable.getOffset(), total);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), total);

        return new PageImpl<>(entities.subList(fromIndex, toIndex), pageable, total);
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
        return findById(id, FakeDBFetchMode.RAW);
    }

    @Override
    public synchronized Optional<T> findById(ID id, FakeDBFetchMode fetchMode) {
        return findAll(fetchMode).stream()
                .filter(entity -> Objects.equals(readId(entity), id))
                .findFirst();
    }

    @Override
    public synchronized T save(T entity) {
        return store.update(database -> {
            List<Object> rows = database.table(schema, table);
            ID id = ensureId(entity, rows);
            int existingIndex = findIndexById(rows, id);
            applyAuditing(entity, existingIndex >= 0);
            validateEntity(entity, rows, id);
            upsert(rows, entity, id);
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
                ID id = ensureId(entity, rows);
                int existingIndex = findIndexById(rows, id);
                applyAuditing(entity, existingIndex >= 0);
                validateEntity(entity, rows, id);
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

    private void resolveReferences(List<T> entities, FakeDBDatabase database) {
        for (T entity : entities) {
            for (Field referenceField : allFields(entityClass)) {
                FakeDBReference reference = referenceField.getAnnotation(FakeDBReference.class);
                if (reference != null) {
                    resolveReference(entity, referenceField, reference, database);
                }
            }
        }
    }

    private void resolveReference(T entity, Field referenceField, FakeDBReference reference, FakeDBDatabase database) {
        Field localField = resolveField(entityClass, reference.localField());
        Object localValue = readField(entity, localField);
        if (localValue == null) {
            writeField(entity, referenceField, reference.multiple() ? List.of() : null);
            return;
        }

        Class<?> targetClass = resolveReferenceTargetClass(referenceField, reference);
        Field targetField = resolveField(targetClass, reference.targetField());
        String targetSchema = reference.schema().isBlank() ? schema : reference.schema();
        List<Object> targetRows = database.table(targetSchema, reference.table());
        List<?> targets = targetRows.stream()
                .map(row -> objectMapper.convertValue(row, targetClass))
                .filter(target -> matchesReference(localValue, readField(target, targetField), reference.multiple()))
                .toList();

        if (reference.multiple()) {
            writeField(entity, referenceField, targets);
        } else {
            writeField(entity, referenceField, targets.stream().findFirst().orElse(null));
        }
    }

    private boolean matchesReference(Object localValue, Object targetValue, boolean multiple) {
        if (multiple && localValue instanceof Collection<?> collection) {
            return collection.contains(targetValue);
        }
        return Objects.equals(localValue, targetValue);
    }

    private Class<?> resolveReferenceTargetClass(Field referenceField, FakeDBReference reference) {
        if (!reference.multiple()) {
            return referenceField.getType();
        }

        Type genericType = referenceField.getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> targetClass) {
            return targetClass;
        }

        throw new FakeDBConfigurationException(
                "FakeDB reference field " + referenceField.getName()
                        + " must declare a concrete generic target type"
        );
    }

    private Comparator<T> comparator(Sort sort) {
        Comparator<T> comparator = null;

        for (Sort.Order order : sort) {
            Comparator<T> orderComparator = (left, right) -> compareValues(
                    readSortableValue(left, order),
                    readSortableValue(right, order),
                    order
            );

            comparator = comparator == null ? orderComparator : comparator.thenComparing(orderComparator);
        }

        return comparator == null ? (left, right) -> 0 : comparator;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareValues(Object left, Object right, Sort.Order order) {
        Comparator nullComparator = order.getNullHandling() == Sort.NullHandling.NULLS_FIRST
                ? Comparator.nullsFirst(Comparator.naturalOrder())
                : Comparator.nullsLast(Comparator.naturalOrder());

        Object leftValue = normalizeSortableValue(left, order);
        Object rightValue = normalizeSortableValue(right, order);
        int result = nullComparator.compare(leftValue, rightValue);
        return order.isAscending() ? result : -result;
    }

    private Object normalizeSortableValue(Object value, Sort.Order order) {
        if (value instanceof String string && order.isIgnoreCase()) {
            return string.toLowerCase();
        }
        return value;
    }

    private Object readSortableValue(T entity, Sort.Order order) {
        String property = order.getProperty();

        try {
            Field field = resolveField(entityClass, property);
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new FakeDBConfigurationException("Cannot read FakeDB sort field " + property, ex);
        }
    }

    private static Field resolveField(Class<?> entityClass, String property) {
        for (Field field : allFields(entityClass)) {
            if (field.getName().equals(property)) {
                return field;
            }

            FakeDBColumn column = field.getAnnotation(FakeDBColumn.class);
            if (column != null && column.value().equals(property)) {
                return field;
            }
        }

        throw new FakeDBConfigurationException(
                "FakeDB entity " + entityClass.getName() + " has no sortable field named " + property
        );
    }

    @SuppressWarnings("unchecked")
    private ID readId(T entity) {
        try {
            return (ID) idField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new FakeDBConfigurationException("Cannot read FakeDB id field " + idField.getName(), ex);
        }
    }

    private ID ensureId(T entity, List<Object> rows) {
        ID id = readId(entity);
        if (id != null) {
            return id;
        }

        FakeDBGeneratedValue generatedValue = idField.getAnnotation(FakeDBGeneratedValue.class);
        if (generatedValue == null) {
            throw new FakeDBConfigurationException("FakeDB entity id cannot be null");
        }

        ID generatedId = generateId(rows, generatedValue.strategy());
        writeId(entity, generatedId);
        return generatedId;
    }

    @SuppressWarnings("unchecked")
    private ID generateId(List<Object> rows, FakeDBGeneratedValue.Strategy strategy) {
        Class<?> idType = idField.getType();

        if (strategy == FakeDBGeneratedValue.Strategy.UUID) {
            UUID uuid = UUID.randomUUID();
            if (idType == UUID.class) {
                return (ID) uuid;
            }
            if (idType == String.class) {
                return (ID) uuid.toString();
            }
            throw new FakeDBConfigurationException(
                    "FakeDB UUID generated ids require a String or UUID id field"
            );
        }

        long nextId = rows.stream()
                .map(item -> objectMapper.convertValue(item, entityClass))
                .map(this::readId)
                .filter(Objects::nonNull)
                .mapToLong(this::toLongId)
                .max()
                .orElse(0L) + 1L;

        if (idType == Long.class || idType == long.class) {
            return (ID) Long.valueOf(nextId);
        }
        if (idType == Integer.class || idType == int.class) {
            return (ID) Integer.valueOf(Math.toIntExact(nextId));
        }
        if (idType == String.class) {
            return (ID) Long.toString(nextId);
        }

        throw new FakeDBConfigurationException(
                "FakeDB INCREMENT generated ids require a Long, Integer, or String id field"
        );
    }

    private long toLongId(ID id) {
        if (id instanceof Number number) {
            return number.longValue();
        }
        if (id instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ex) {
                throw new FakeDBConfigurationException("FakeDB cannot increment non-numeric String id " + string, ex);
            }
        }

        throw new FakeDBConfigurationException("FakeDB cannot increment id value " + id);
    }

    private void writeId(T entity, ID id) {
        try {
            idField.set(entity, id);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new FakeDBConfigurationException(
                    "Cannot write generated FakeDB id field " + idField.getName()
                            + ". Generated ids require a writable id field.",
                    ex
            );
        }
    }

    private void applyAuditing(T entity, boolean existingEntity) {
        for (Field field : allFields(entityClass)) {
            if (field.isAnnotationPresent(FakeDBCreatedDate.class) && !existingEntity && readField(entity, field) == null) {
                writeField(entity, field, nowValue(field));
            }

            if (field.isAnnotationPresent(FakeDBLastModifiedDate.class)) {
                writeField(entity, field, nowValue(field));
            }
        }
    }

    private Object nowValue(Field field) {
        Class<?> type = field.getType();
        if (type == Instant.class) {
            return Instant.now();
        }
        if (type == Date.class) {
            return Date.from(Instant.now());
        }
        if (type == String.class) {
            return Instant.now().toString();
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.now();
        }
        if (type == OffsetDateTime.class) {
            return OffsetDateTime.now();
        }
        if (type == ZonedDateTime.class) {
            return ZonedDateTime.now();
        }

        throw new FakeDBConfigurationException(
                "FakeDB auditing field " + field.getName()
                + " must be Instant, LocalDateTime, OffsetDateTime, ZonedDateTime, Date, or String"
        );
    }

    private void validateEntity(T entity, List<Object> rows, ID id) {
        for (Field field : allFields(entityClass)) {
            Object value = readField(entity, field);

            if (field.isAnnotationPresent(FakeDBNotNull.class) && value == null) {
                throw new FakeDBConstraintViolationException(
                        "FakeDB field " + field.getName() + " cannot be null"
                );
            }

            if (field.isAnnotationPresent(FakeDBUnique.class) && value != null) {
                validateUniqueField(field, value, rows, id);
            }
        }
    }

    private void validateUniqueField(Field field, Object value, List<Object> rows, ID id) {
        for (Object row : rows) {
            T current = objectMapper.convertValue(row, entityClass);
            if (Objects.equals(readId(current), id)) {
                continue;
            }

            Object currentValue = readField(current, field);
            if (Objects.equals(currentValue, value)) {
                throw new FakeDBConstraintViolationException(
                        "FakeDB unique field " + field.getName() + " already contains value " + value
                );
            }
        }
    }

    private Object readField(Object entity, Field field) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new FakeDBConfigurationException("Cannot read FakeDB field " + field.getName(), ex);
        }
    }

    private void writeField(Object entity, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new FakeDBConfigurationException(
                    "Cannot write FakeDB field " + field.getName()
                            + ". Generated and audited fields require writable fields.",
                    ex
            );
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
        for (Field field : allFields(entityClass)) {
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

    private static List<Field> allFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = entityClass;

        while (current != null && current != Object.class) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }
}
