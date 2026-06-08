package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;

class FakeDBAnnotationIntrospector extends JacksonAnnotationIntrospector {

    @Override
    public PropertyName findNameForSerialization(Annotated annotated) {
        PropertyName columnName = findColumnName(annotated);
        return columnName != null ? columnName : super.findNameForSerialization(annotated);
    }

    @Override
    public PropertyName findNameForDeserialization(Annotated annotated) {
        PropertyName columnName = findColumnName(annotated);
        return columnName != null ? columnName : super.findNameForDeserialization(annotated);
    }

    private PropertyName findColumnName(Annotated annotated) {
        FakeDBColumn column = _findAnnotation(annotated, FakeDBColumn.class);
        if (column == null || column.value().isBlank()) {
            return null;
        }
        return PropertyName.construct(column.value());
    }
}
