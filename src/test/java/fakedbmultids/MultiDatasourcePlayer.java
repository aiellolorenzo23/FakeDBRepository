package fakedbmultids;

import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBTable;

@FakeDBTable("players")
public record MultiDatasourcePlayer(@FakeDBId Long id, String name) {
}
