package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FakeDBDatasources {

    private static final String LEGACY_DATASOURCE_NAME = "default";

    private final Map<String, FakeDBTemplate> templates;

    private final String defaultDatasourceName;

    public FakeDBDatasources(FakeDBProperties properties, ObjectMapper objectMapper) {
        Map<String, FakeDBTemplate> createdTemplates = new LinkedHashMap<>();

        if (hasText(properties.getPath()) || properties.getDatasources().isEmpty()) {
            createdTemplates.put(LEGACY_DATASOURCE_NAME, new FakeDBTemplate(properties, objectMapper));
        }

        properties.getDatasources().forEach((name, datasource) -> {
            if (name == null || name.isBlank()) {
                throw new FakeDBConfigurationException("FakeDB datasource name cannot be blank");
            }
            createdTemplates.put(name, new FakeDBTemplate(properties.copyForDatasource(datasource), objectMapper));
        });

        this.defaultDatasourceName = resolveDefaultDatasourceName(properties, createdTemplates);
        this.templates = Collections.unmodifiableMap(createdTemplates);
    }

    public FakeDBTemplate defaultTemplate() {
        if (defaultDatasourceName == null) {
            throw new FakeDBConfigurationException(
                    "FakeDB path is required. Configure fakedb.path or fakedb.default-datasource"
            );
        }
        return template(defaultDatasourceName);
    }

    public FakeDBTemplate template(String datasourceName) {
        if (datasourceName == null || datasourceName.isBlank()) {
            return defaultTemplate();
        }

        FakeDBTemplate template = templates.get(datasourceName);
        if (template == null) {
            throw new FakeDBConfigurationException("Unknown FakeDB datasource: " + datasourceName);
        }
        return template;
    }

    public <T, ID> FakeDBRepository<T, ID> repository(
            String datasourceName,
            Class<T> entityClass,
            Class<ID> idClass
    ) {
        return template(datasourceName).repository(entityClass, idClass);
    }

    public <T, ID> FakeDBRepository<T, ID> repository(
            String datasourceName,
            String table,
            Class<T> entityClass,
            Class<ID> idClass
    ) {
        return template(datasourceName).repository(table, entityClass, idClass);
    }

    public <T, ID> FakeDBRepository<T, ID> repository(
            String datasourceName,
            String schema,
            String table,
            Class<T> entityClass,
            Class<ID> idClass
    ) {
        return template(datasourceName).repository(schema, table, entityClass, idClass);
    }

    public Set<String> names() {
        return templates.keySet();
    }

    private String resolveDefaultDatasourceName(
            FakeDBProperties properties,
            Map<String, FakeDBTemplate> createdTemplates
    ) {
        if (hasText(properties.getDefaultDatasource())) {
            String datasourceName = properties.getDefaultDatasource();
            if (!createdTemplates.containsKey(datasourceName)) {
                throw new FakeDBConfigurationException("Unknown FakeDB default datasource: " + datasourceName);
            }
            return datasourceName;
        }

        if (createdTemplates.containsKey(LEGACY_DATASOURCE_NAME)) {
            return LEGACY_DATASOURCE_NAME;
        }

        if (!createdTemplates.isEmpty()) {
            return createdTemplates.keySet().iterator().next();
        }

        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
