package io.github.aiellolorenzo23.fakedb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fakedb")
public class FakeDBProperties {

    private boolean enabled = true;

    private String path;

    private String defaultSchema = "main";

    private boolean autoCreate = true;

    private boolean prettyPrint = true;

    private boolean backupOnSave = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
}
