package fakedbmultids;

import io.github.aiellolorenzo23.fakedb.annotation.FakeDBDatasource;
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;

@FakeDBDatasource("players")
public interface MultiDatasourcePlayerRepository extends FakeDBRepository<MultiDatasourcePlayer, Long> {
}
