package io.github.aiellolorenzo23.fakedb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "fakedb")
public class FakeDBProperties {

    private boolean enabled = true;

    private String defaultDatasource;

    private String path;

    private String database = "default";

    private String defaultSchema = "main";

    private boolean autoCreate = true;

    private boolean prettyPrint = true;

    private boolean backupOnSave = false;

    private NamingStrategy namingStrategy = NamingStrategy.IDENTITY;

    private boolean failOnUnknownProperties = true;

    private Map<String, DatasourceProperties> datasources = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultDatasource() {
        return defaultDatasource;
    }

    public void setDefaultDatasource(String defaultDatasource) {
        this.defaultDatasource = defaultDatasource;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public boolean isBackupOnSave() {
        return backupOnSave;
    }

    public void setBackupOnSave(boolean backupOnSave) {
        this.backupOnSave = backupOnSave;
    }

    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy == null ? NamingStrategy.IDENTITY : namingStrategy;
    }

    public boolean isFailOnUnknownProperties() {
        return failOnUnknownProperties;
    }

    public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
        this.failOnUnknownProperties = failOnUnknownProperties;
    }

    public Map<String, DatasourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, DatasourceProperties> datasources) {
        this.datasources = datasources == null ? new LinkedHashMap<>() : datasources;
    }

    public FakeDBProperties copyForDatasource(DatasourceProperties datasource) {
        FakeDBProperties copy = new FakeDBProperties();
        copy.setEnabled(enabled);
        copy.setDefaultDatasource(defaultDatasource);
        copy.setPath(datasource.path == null ? path : datasource.path);
        copy.setDatabase(datasource.database == null ? database : datasource.database);
        copy.setDefaultSchema(datasource.defaultSchema == null ? defaultSchema : datasource.defaultSchema);
        copy.setAutoCreate(datasource.autoCreate == null ? autoCreate : datasource.autoCreate);
        copy.setPrettyPrint(datasource.prettyPrint == null ? prettyPrint : datasource.prettyPrint);
        copy.setBackupOnSave(datasource.backupOnSave == null ? backupOnSave : datasource.backupOnSave);
        copy.setNamingStrategy(datasource.namingStrategy == null ? namingStrategy : datasource.namingStrategy);
        copy.setFailOnUnknownProperties(
                datasource.failOnUnknownProperties == null
                        ? failOnUnknownProperties
                        : datasource.failOnUnknownProperties
        );
        return copy;
    }

    public static class DatasourceProperties {

        private String path;

        private String database;

        private String defaultSchema;

        private Boolean autoCreate;

        private Boolean prettyPrint;

        private Boolean backupOnSave;

        private NamingStrategy namingStrategy;

        private Boolean failOnUnknownProperties;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getDefaultSchema() {
            return defaultSchema;
        }

        public void setDefaultSchema(String defaultSchema) {
            this.defaultSchema = defaultSchema;
        }

        public Boolean getAutoCreate() {
            return autoCreate;
        }

        public void setAutoCreate(Boolean autoCreate) {
            this.autoCreate = autoCreate;
        }

        public Boolean getPrettyPrint() {
            return prettyPrint;
        }

        public void setPrettyPrint(Boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
        }

        public Boolean getBackupOnSave() {
            return backupOnSave;
        }

        public void setBackupOnSave(Boolean backupOnSave) {
            this.backupOnSave = backupOnSave;
        }

        public NamingStrategy getNamingStrategy() {
            return namingStrategy;
        }

        public void setNamingStrategy(NamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
        }

        public Boolean getFailOnUnknownProperties() {
            return failOnUnknownProperties;
        }

        public void setFailOnUnknownProperties(Boolean failOnUnknownProperties) {
            this.failOnUnknownProperties = failOnUnknownProperties;
        }
    }

    public enum NamingStrategy {
        IDENTITY,
        SNAKE_CASE
    }
}
