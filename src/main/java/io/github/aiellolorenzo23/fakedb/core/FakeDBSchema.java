package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeDBSchema {

    private final Map<String, List<Object>> tables = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, List<Object>> anyTables() {
        return tables;
    }

    @JsonAnySetter
    public void setTable(String name, List<Object> rows) {
        tables.put(name, rows == null ? new ArrayList<>() : rows);
    }

    @JsonIgnore
    public Map<String, List<Object>> getTables() {
        return tables;
    }

    public List<Object> table(String name) {
        return tables.computeIfAbsent(name, ignored -> new ArrayList<>());
    }
}