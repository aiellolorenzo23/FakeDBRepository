package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;

final class FakeDBObjectMapper {

    private FakeDBObjectMapper() {
    }

    static ObjectMapper configure(ObjectMapper objectMapper) {
        return configure(objectMapper, null);
    }

    static ObjectMapper configure(ObjectMapper objectMapper, FakeDBProperties properties) {
        AnnotationIntrospector existing = objectMapper.getSerializationConfig().getAnnotationIntrospector();
        objectMapper.setAnnotationIntrospector(AnnotationIntrospector.pair(
                new FakeDBAnnotationIntrospector(),
                existing
        ));

        if (properties != null) {
            if (properties.getNamingStrategy() == FakeDBProperties.NamingStrategy.SNAKE_CASE) {
                objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            }

            objectMapper.configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    properties.isFailOnUnknownProperties()
            );
        }

        return objectMapper;
    }
}
