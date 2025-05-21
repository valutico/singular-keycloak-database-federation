package org.opensingular.dbuserprovider;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import java.util.Map;
import java.util.stream.Stream;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.RDBMS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@JBossLog
@AutoService(UserStorageProviderFactory.class)
@SuppressWarnings("rawtypes") // Added to suppress AutoService warning
public class DBUserStorageProviderFactory implements 
        UserStorageProviderFactory<DBUserStorageProvider>,
        UserLookupProvider,
        UserQueryProvider, 
        CredentialInputUpdater, 
        CredentialInputValidator, 
        UserRegistrationProvider {
    
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
    }
    
    @Override
    public void close() {
        for (Map.Entry<String, ProviderConfig> pc : providerConfigPerInstance.entrySet()) {
            pc.getValue().dataSourceProvider.close();
        }
    }
    
    @Override
    public DBUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        ProviderConfig providerConfig = providerConfigPerInstance.computeIfAbsent(model.getId(), s -> configure(model));
        return new DBUserStorageProvider(session, model, providerConfig.dataSourceProvider, providerConfig.queryConfigurations);
    }
    
    private synchronized ProviderConfig configure(ComponentModel model) {
        log.infov("Creating configuration for model: id={0} name={1}", model.getId(), model.getName());
        ProviderConfig providerConfig = new ProviderConfig();
        String         user           = model.get("user");
        String         password       = model.get("password");
        String         url            = model.get("url");
        RDBMS          rdbms          = RDBMS.getByDescription(model.get("rdbms"));
        providerConfig.dataSourceProvider.configure(url, rdbms, user, password, model.getName());
        providerConfig.queryConfigurations = new QueryConfigurations(
                model.get("count"),
                model.get("listAll"),
                model.get("findById"),
                model.get("findByUsername"),
                model.get("findBySearchTerm"),
                model.get("findPasswordHash"),
                model.get("hashFunction"),
                rdbms,
                model.get("allowKeycloakDelete", false),
                model.get("allowDatabaseToOverwriteKeycloak", false),
                model.get("syncEnabled", false), 
                model.get("unlinkEnabled", false)
        );
        return providerConfig;
    }
    
    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
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
                                           //DATABASE
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
                                           .property()
                                           .name("allowKeycloakDelete")
                                           .label("Allow Keycloak's User Delete")
                                           .helpText("By default, clicking Delete on a user in Keycloak is not allowed.  Activate this option to allow to Delete Keycloak's version of the user (does not touch the user record in the linked RDBMS), e.g. to clear synching issues and allow the user to be synced from scratch from the RDBMS on next use, in Production or for testing.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
                                           .property()
                                           .name("allowDatabaseToOverwriteKeycloak")
                                           .label("Allow DB Attributes to Overwrite Keycloak")
                                           // Technical details for the following comment: we aggregate both the existing Keycloak version and the DB version of an attribute in a Set, but since e.g. email is not a list of values on the Keycloak User, the new email is never set on it.
                                           .helpText("By default, once a user is loaded in Keycloak, its attributes (e.g. 'email') stay as they are in Keycloak even if an attribute of the same name now returns a different value through the query.  Activate this option to have all attributes set in the SQL query to always overwrite the existing user attributes in Keycloak (e.g. if Keycloak user has email 'test@test.com' but the query fetches a field named 'email' that has a value 'example@exemple.com', the Keycloak user will now have email attribute = 'example@exemple.com'). This behavior works with NO_CAHCE configuration. In case you set this flag under a cached configuration, the user attributes will be reload if: 1) the cached value is older than 500ms and 2) username or e-mail does not match cached values.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
                                           .property()
                                           .name("syncEnabled")
                                           .label("Enable User Synchronization")
                                           .helpText("If enabled, user data from the external database will be synchronized to Keycloak's local storage.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
                                           .property()
                                           .name("unlinkEnabled")
                                           .label("Enable User Unlinking")
                                           .helpText("If enabled, users can be unlinked from the federation, allowing them to set local Keycloak credentials.")
                                           .type(ProviderConfigProperty.BOOLEAN_TYPE)
                                           .defaultValue("false")
                                           .add()
        
                                           //QUERIES
        
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
                                           .defaultValue("select \"id\"," +
                                                         "            \"username\"," +
                                                         "            \"email\"," +
                                                         "            \"firstName\"," +
                                                         "            \"lastName\"," +
                                                         "            \"cpf\"," +
                                                         "            \"fullName\" from users ")
                                           .add()
        
                                           .property()
                                           .name("findById")
                                           .label("Find user by id SQL query")
                                           .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "user id") + PARAMETER_PLACEHOLDER_HELP)
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("select \"id\"," +
                                                         "            \"username\"," +
                                                         "            \"email\"," +
                                                         "            \"firstName\"," +
                                                         "            \"lastName\"," +
                                                         "            \"cpf\"," +
                                                         "            \"fullName\" from users where \"id\" = ? ")
                                           .add()
        
                                           .property()
                                           .name("findByUsername")
                                           .label("Find user by username SQL query")
                                           .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "user username") + PARAMETER_PLACEHOLDER_HELP)
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("select \"id\"," +
                                                         "            \"username\"," +
                                                         "            \"email\"," +
                                                         "            \"firstName\"," +
                                                         "            \"lastName\"," +
                                                         "            \"cpf\"," +
                                                         "            \"fullName\" from users where \"username\" = ? ")
                                           .add()
        
                                           .property()
                                           .name("findBySearchTerm")
                                           .label("Find user by search term SQL query")
                                           .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "search term") + PARAMETER_PLACEHOLDER_HELP)
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("select \"id\"," +
                                                         "            \"username\"," +
                                                         "            \"email\"," +
                                                         "            \"firstName\"," +
                                                         "            \"lastName\"," +
                                                         "            \"cpf\"," +
                                                         "            \"fullName\" from users where upper(\"username\") like (?)  or upper(\"email\") like (?) or upper(\"fullName\") like (?)")
                                           .add()
        
                                           .property()
                                           .name("findPasswordHash")
                                           .label("Find password hash (blowfish or hash digest hex) SQL query")
                                           .helpText(DEFAULT_HELP_TEXT + String.format(PARAMETER_HELP, "user username") + PARAMETER_PLACEHOLDER_HELP)
                                           .type(ProviderConfigProperty.STRING_TYPE)
                                           .defaultValue("select hash_pwd from users where \"username\" = ? ")
                                           .add()
                                           .property()
                                           .name("hashFunction")
                                           .label("Password hash function")
                                           .helpText("Hash type used to match passwrod (md* e sha* uses hex hash digest)")
                                           .type(ProviderConfigProperty.LIST_TYPE)
                                           .options("Blowfish (bcrypt)", "MD2", "MD5", "SHA-1", "SHA-256", "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512", "SHA-384", "SHA-512/224", "SHA-512/256", "SHA-512", "PBKDF2-SHA256")
                                           .defaultValue("SHA-1")
                                           .add()
                                           .build();
    }
    
    private static class ProviderConfig {
        private DataSourceProvider  dataSourceProvider = new DataSourceProvider();
        private QueryConfigurations queryConfigurations;
    }
    
    // UserLookupProvider implementation
    @Override
    public UserModel getUserById(KeycloakSession session, RealmModel realm, String id) {
        return getProvider(session).getUserById(session, realm, id);
    }

    @Override
    public UserModel getUserByUsername(KeycloakSession session, RealmModel realm, String username) {
        return getProvider(session).getUserByUsername(session, realm, username);
    }

    @Override
    public UserModel getUserByEmail(KeycloakSession session, RealmModel realm, String email) {
        return getProvider(session).getUserByEmail(session, realm, email);
    }

    // UserQueryProvider implementation
    @Override
    public int getUsersCount(KeycloakSession session, RealmModel realm) {
        return getProvider(session).getUsersCount(session, realm);
    }

    @Override
    public Stream<UserModel> searchForUserStream(KeycloakSession session, RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        return getProvider(session).searchForUserStream(session, realm, params, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(KeycloakSession session, RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        return getProvider(session).searchForUserStream(session, realm, search, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(KeycloakSession session, RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        return getProvider(session).getGroupMembersStream(session, realm, group, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(KeycloakSession session, RealmModel realm, String attrName, String attrValue) {
        return getProvider(session).searchForUserByUserAttributeStream(session, realm, attrName, attrValue);
    }

    // CredentialInputValidator implementation
    @Override
    public boolean supportsCredentialType(String credentialType) {
        return getProvider(null).supportsCredentialType(credentialType);
    }

    @Override
    public boolean isConfiguredFor(KeycloakSession session, RealmModel realm, UserModel user, String credentialType) {
        return getProvider(session).isConfiguredFor(session, realm, user, credentialType);
    }

    @Override
    public boolean isValid(KeycloakSession session, RealmModel realm, UserModel user, CredentialInput input) {
        return getProvider(session).isValid(session, realm, user, input);
    }

    // CredentialInputUpdater implementation
    @Override
    public boolean updateCredential(KeycloakSession session, RealmModel realm, UserModel user, CredentialInput input) {
        return getProvider(session).updateCredential(session, realm, user, input);
    }

    @Override
    public void disableCredentialType(KeycloakSession session, RealmModel realm, UserModel user, String credentialType) {
        getProvider(session).disableCredentialType(session, realm, user, credentialType);
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(KeycloakSession session, RealmModel realm, UserModel user) {
        return getProvider(session).getDisableableCredentialTypesStream(session, realm, user);
    }

    // UserRegistrationProvider implementation
    @Override
    public UserModel addUser(KeycloakSession session, RealmModel realm, String username) {
        return getProvider(session).addUser(session, realm, username);
    }

    @Override
    public boolean removeUser(KeycloakSession session, RealmModel realm, UserModel user) {
        return getProvider(session).removeUser(session, realm, user);
    }

    // Helper method to get a provider instance
    private DBUserStorageProvider getProvider(KeycloakSession session) {
        if (session == null) {
            // This is used only for supportsCredentialType which doesn't need a session
            return new DBUserStorageProvider(null, null, null, null) {
                @Override
                public boolean supportsCredentialType(String credentialType) {
                    return PasswordCredentialModel.TYPE.equals(credentialType);
                }
            };
        }
        
        ComponentModel componentModel = null;
        String realmId = null;
        
        if (session.getContext() != null && session.getContext().getRealm() != null) {
            realmId = session.getContext().getRealm().getId();
            componentModel = session.getContext().getRealm().getComponentsStream()
                .filter(component -> component.getProviderId().equals(getId()))
                .findFirst()
                .orElse(null);
        }
        
        if (componentModel == null) {
            throw new IllegalStateException("Could not find component model for provider: " + getId());
        }
        
        return create(session, componentModel);
    }
}
