package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;

final class FakeDBObjectMapper {

    private FakeDBObjectMapper() {
    }

    static ObjectMapper configure(ObjectMapper objectMapper) {
        AnnotationIntrospector existing = objectMapper.getSerializationConfig().getAnnotationIntrospector();
        objectMapper.setAnnotationIntrospector(AnnotationIntrospector.pair(
                new FakeDBAnnotationIntrospector(),
                existing
        ));
        return objectMapper;
    }
}
