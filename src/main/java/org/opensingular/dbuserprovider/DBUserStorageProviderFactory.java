package org.opensingular.dbuserprovider;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.RDBMS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JBossLog
@AutoService(UserStorageProviderFactory.class)
public class DBUserStorageProviderFactory implements UserStorageProviderFactory<DBUserStorageProvider> {
    
    private static final String PARAMETER_PLACEHOLDER_HELP = "Use '?' as parameter placeholder character (replaced only once). ";
    private static final String DEFAULT_HELP_TEXT          = "Select to query all users you must return at least: \"id\". " +
                                                             "            \"username\"," +
                                                             "            \"email\" (optional)," +
                                                             "            \"firstName\" (optional)," +
                                                             "            \"lastName\" (optional). Any other parameter can be mapped by aliases to a realm scope";
    private static final String PARAMETER_HELP             = " The %s is passed as query parameter.";
    
    private Map<String, ProviderConfig> providerConfigPerInstance = new HashMap<>();
    
    @Override
    public void init(Config.Scope config) {
        log.info("Initializing DBUserStorageProviderFactory");
    }
    
    @Override
    public void close() {
        log.info("Closing DBUserStorageProviderFactory");
        for (Map.Entry<String, ProviderConfig> pc : providerConfigPerInstance.entrySet()) {
            pc.getValue().dataSourceProvider.close();
        }
    }
    
    @Override
    public DBUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return create(session, (UserStorageProviderModel) model);
    }
    
    public DBUserStorageProvider create(KeycloakSession session, UserStorageProviderModel model) {
        ProviderConfig providerConfig = providerConfigPerInstance.computeIfAbsent(model.getId(), s -> configure(model));
        return new DBUserStorageProvider(session, model, providerConfig.dataSourceProvider, providerConfig.queryConfigurations);
    }
    
    private synchronized ProviderConfig configure(UserStorageProviderModel model) {
        log.infov("Creating configuration for model: id={0} name={1}", model.getId(), model.getName());
        ProviderConfig providerConfig = new ProviderConfig();
        String user = model.get("user");
        String password = model.get("password");
        String url = model.get("url");
        RDBMS rdbms = RDBMS.getByDescription(model.get("rdbms"));
        
        providerConfig.dataSourceProvider.configure(url, rdbms, user, password, model.getName());
        providerConfig.queryConfigurations = new QueryConfigurations(
                model.get("count"),
                model.get("listAll"),
                model.get("findById"),
                model.get("findByUsername"),
                model.get("findByEmail"),
                model.get("findBySearchTerm"),
                model.get("findPasswordHash"),
                model.get("hashFunction"),
                rdbms,
                model.get("allowKeycloakDelete", false),
                model.get("allowDatabaseToOverwriteKeycloak", false),
                model.get("syncEnabled", false),
                model.get("listAllForSync", model.get("listAll"))
        );
        return providerConfig;
    }
    
    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        validateConfiguration(session, realm, (UserStorageProviderModel) model);
    }
    
    public void validateConfiguration(KeycloakSession session, RealmModel realm, UserStorageProviderModel model) throws ComponentValidationException {
        try {
            ProviderConfig old = providerConfigPerInstance.put(model.getId(), configure(model));
            if (old != null) {
                old.dataSourceProvider.close();
            }
        } catch (Exception e) {
            throw new ComponentValidationException(e.getMessage(), e);
        }
    }
    
    @Override
    public String getId() {
        return "singular-db-user-provider";
    }
    
    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                // Database configuration
                .property()
                .name("url")
                .label("JDBC URL")
                .helpText("JDBC Connection String")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("jdbc:jtds:sqlserver://server-name/database_name;instance=instance_name")
                .add()
                .property()
                .name("user")
                .label("JDBC Connection User")
                .helpText("JDBC Connection User")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("user")
                .add()
                .property()
                .name("password")
                .label("JDBC Connection Password")
                .helpText("JDBC Connection Password")
                .type(ProviderConfigProperty.PASSWORD)
                .defaultValue("password")
                .add()
                .property()
                .name("rdbms")
                .label("RDBMS")
                .helpText("Relational Database Management System")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options(RDBMS.getAllDescriptions())
                .defaultValue(RDBMS.SQL_SERVER.getDesc())
                .add()
                
                // User management options
                .property()
                .name("allowKeycloakDelete")
                .label("Allow Keycloak's User Delete")
                .helpText("Allow deletion of users in Keycloak (does not affect the external database)")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
                .add()
                .property()
                .name("allowDatabaseToOverwriteKeycloak")
                .label("Allow DB Attributes to Overwrite Keycloak")
                .helpText("Allow external database attributes to overwrite Keycloak user attributes")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
                .add()
                .property()
                .name("syncEnabled")
                .label("Enable Sync")
                .helpText("Enable user synchronization with Keycloak")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
                .add()
                
                // Query configurations
                .property()
                .name("count")
                .label("User count SQL query")
                .helpText("SQL query returning the total count of users")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select count(*) from users")
                .add()
                .property()
                .name("listAll")
                .label("List All Users SQL query")
                .helpText(DEFAULT_HELP_TEXT)
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select id, username, email, firstName, lastName from users")
                .add()
                .property()
                .name("listAllForSync")
                .label("List All Users SQL query (for sync)")
                .helpText(DEFAULT_HELP_TEXT + " Used for user synchronization")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("")
                .add()
                .property()
                .name("findById")
                .label("Find user by id SQL query")
                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "user id"))
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select id, username, email, firstName, lastName from users where id = ?")
                .add()
                .property()
                .name("findByUsername")
                .label("Find user by username SQL query")
                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "username"))
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select id, username, email, firstName, lastName from users where username = ?")
                .add()
                .property()
                .name("findByEmail")
                .label("Find user by email SQL query")
                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "email"))
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select id, username, email, firstName, lastName from users where email = ?")
                .add()
                .property()
                .name("findBySearchTerm")
                .label("Find user by search term SQL query")
                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "search term"))
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select id, username, email, firstName, lastName from users where username like ? or email like ?")
                .add()
                .property()
                .name("findPasswordHash")
                .label("Find password hash SQL query")
                .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "username"))
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("select password_hash from users where username = ?")
                .add()
                .property()
                .name("hashFunction")
                .label("Password hash function")
                .helpText("Hash type used to match password")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("Blowfish (bcrypt)", "MD5", "SHA-1", "SHA-256", "SHA-512", "PBKDF2-SHA256", "Argon2id")
                .defaultValue("SHA-1")
                .add()
                .build();
    }
    
    private static class ProviderConfig {
        private final DataSourceProvider dataSourceProvider = new DataSourceProvider();
        private QueryConfigurations queryConfigurations;
    }
}
