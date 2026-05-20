package io.github.aiellolorenzo23.fakedb.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeDBSchema {

    private Map<String, List<Object>> tables = new LinkedHashMap<>();

    public Map<String, List<Object>> getTables() {
        return tables;
    }

    public void setTables(Map<String, List<Object>> tables) {
        this.tables = tables == null ? new LinkedHashMap<>() : tables;
    }

    public List<Object> table(String name) {
        return tables.computeIfAbsent(name, ignored -> new ArrayList<>());
    }
}
