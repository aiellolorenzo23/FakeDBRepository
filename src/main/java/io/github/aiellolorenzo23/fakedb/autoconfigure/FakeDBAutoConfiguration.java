package io.github.aiellolorenzo23.fakedb.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.core.FakeDBDatasources;
import io.github.aiellolorenzo23.fakedb.core.FakeDBTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FakeDBProperties.class)
@ConditionalOnProperty(prefix = "fakedb", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FakeDBAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper fakeDBObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public FakeDBDatasources fakeDBDatasources(FakeDBProperties properties, ObjectMapper objectMapper) {
        return new FakeDBDatasources(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FakeDBTemplate fakeDBTemplate(FakeDBDatasources fakeDBDatasources) {
        return fakeDBDatasources.defaultTemplate();
    }
}
