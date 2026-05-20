package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBTable;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;

public class FakeDBTemplate {

    private final FakeDBProperties properties;
    private final ObjectMapper objectMapper;
    private final FakeDBStore store;

    public FakeDBTemplate(FakeDBProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.store = new FakeDBStore(properties, objectMapper);
    }

    public <T, ID> FakeDBRepository<T, ID> repository(
            String schema,
            String table,
            Class<T> entityClass,
            Class<ID> idClass
    ) {
        return new JsonFileFakeDBRepository<>(
                store,
                objectMapper,
                schema,
                table,
                entityClass,
                idClass
        );
    }

    public <T, ID> FakeDBRepository<T, ID> repository(String table, Class<T> entityClass, Class<ID> idClass) {
        return repository(properties.getDefaultSchema(), table, entityClass, idClass);
    }

    public <T, ID> FakeDBRepository<T, ID> repository(Class<T> entityClass, Class<ID> idClass) {
        FakeDBTable table = entityClass.getAnnotation(FakeDBTable.class);

        if (table == null) {
            return repository(entityClass.getSimpleName(), entityClass, idClass);
        }

        String schema = table.schema().isBlank() ? properties.getDefaultSchema() : table.schema();

        return repository(schema, table.value(), entityClass, idClass);
    }
}