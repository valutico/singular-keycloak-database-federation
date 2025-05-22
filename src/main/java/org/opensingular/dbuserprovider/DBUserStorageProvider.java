package org.opensingular.dbuserprovider;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.cache.CachedUserModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.ImportSynchronization;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.model.UserAdapter;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.UserRepository;
import org.opensingular.dbuserprovider.util.PagingUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@JBossLog
public class DBUserStorageProvider implements UserStorageProvider,
                                              UserLookupProvider, UserQueryProvider, CredentialInputUpdater, CredentialInputValidator, UserRegistrationProvider, ImportSynchronization {
    
    private final KeycloakSession session;
    private final ComponentModel  model;
    private final UserRepository  repository;
    private final boolean allowDatabaseToOverwriteKeycloak;
    private final boolean syncEnabled;

    DBUserStorageProvider(KeycloakSession session, ComponentModel model, DataSourceProvider dataSourceProvider, QueryConfigurations queryConfigurations) {
        this.session    = session;
        this.model      = model;
        this.repository = new UserRepository(dataSourceProvider, queryConfigurations);
        this.allowDatabaseToOverwriteKeycloak = queryConfigurations.getAllowDatabaseToOverwriteKeycloak();
        this.syncEnabled = queryConfigurations.isSyncEnabled();
    }
    
    
    private Stream<UserModel> toUserModel(RealmModel realm, List<Map<String, String>> users) {
        return users.stream()
                    .map(m -> new UserAdapter(session, realm, model, m, allowDatabaseToOverwriteKeycloak));
    }
    
    
    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }
    
    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }
    
    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        
        log.infov("isValid user credential: userId={0}", user.getId());
        
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
            return false;
        }
        
        UserCredentialModel cred = (UserCredentialModel) input;

        UserModel dbUser = user;
        // If the cache just got loaded in the last 500 millisec (i.e. probably part of the actual flow), there is no point in reloading the user.)
        if (allowDatabaseToOverwriteKeycloak && user instanceof CachedUserModel && (System.currentTimeMillis() - ((CachedUserModel) user).getCacheTimestamp()) > 500) {
          dbUser = this.getUserById(realm, user.getId());

          if (dbUser == null) {
            ((CachedUserModel) user).invalidate();
            return false;
          }

          // For now, we'll just invalidate the cache if username or email has changed. Eventually we could check all (or a parametered list of) attributes fetched from the DB.
          if (!java.util.Objects.equals(user.getUsername(), dbUser.getUsername()) || !java.util.Objects.equals(user.getEmail(), dbUser.getEmail())) {
            ((CachedUserModel) user).invalidate();
          }
        }
        return repository.validateCredentials(dbUser.getUsername(), cred.getChallengeResponse());
    }
    
    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        
        log.infov("updating credential: realm={0} user={1}", realm.getId(), user.getUsername());
        
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
            return false;
        }
        
        UserCredentialModel cred = (UserCredentialModel) input;

        if (user.getFederationLink() != null && user.getFederationLink().equals(model.getId())) {
            // User is linked to this provider. Attempt to update in external DB.
            // This is expected to either fail or do nothing as per provider's capability.
            return repository.updateCredentials(user.getUsername(), cred.getChallengeResponse());
        } else {
            // User is not linked to this provider or is unlinked.
            // Keycloak should handle the password update locally.
            log.infov("User {0} is not directly federated with this provider or is unlinked. Allowing Keycloak to handle password update.", user.getUsername());
            return false;
        }
    }
    
    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
    }
    
    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user)
    {
        return Stream.empty();
    }
    
    @Override
    public void preRemove(RealmModel realm) {
        
        log.infov("pre-remove realm");
    }
    
    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        
        log.infov("pre-remove group");
    }
    
    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        
        log.infov("pre-remove role");
    }
    
    @Override
    public void close() {
        log.debugv("closing");
    }
    
    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        
        log.infov("lookup user by id: realm={0} userId={1}", realm.getId(), id);
        
        String externalId = StorageId.externalId(id);
        Map<String, String> user = repository.findUserById(externalId);

        if (user == null) {
            log.debugv("findUserById returned null, skipping creation of UserAdapter, expect login error");
            return null;
        } else {
            return new UserAdapter(session, realm, model, user, allowDatabaseToOverwriteKeycloak);
        }
    }
    
    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        
        log.infov("lookup user by username: realm={0} username={1}", realm.getId(), username);
        
        return repository.findUserByUsername(username).map(u -> new UserAdapter(session, realm, model, u, allowDatabaseToOverwriteKeycloak)).orElse(null);
    }
    
    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        
        log.infov("lookup user by email: realm={0} email={1}", realm.getId(), email);
        
        return repository.findUserByEmail(email).map(u -> new UserAdapter(session, realm, model, u, allowDatabaseToOverwriteKeycloak)).orElse(null);
    }
    
    @Override
    public int getUsersCount(RealmModel realm) {
        return repository.getUsersCount(null);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, Set<String> groupIds) {
        return repository.getUsersCount(null);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return repository.getUsersCount(search);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, String search, Set<String> groupIds) {
        return repository.getUsersCount(search);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return repository.getUsersCount(null);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params, Set<String> groupIds) {
        return repository.getUsersCount(null);
    }
    
    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return repository.getUsersCount(null);
    }
    
    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search, Integer firstResult,
        Integer maxResults)
    {
        log.infov("list users: realm={0} firstResult={1} maxResults={2}", realm.getId(), firstResult, maxResults);
        return internalSearchForUser(search, realm, new PagingUtil.Pageable(firstResult, maxResults));
    }
    
    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult,
        Integer maxResults)
    {
        String searchTerm = params.getOrDefault("keycloak.session.realm.users.query.search", "");
        log.infov("search for users with params: realm={0} params={1}", realm.getId(), params);
        return internalSearchForUser(searchTerm, realm, null);
    }
    
    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue)
    {
        log.infov("search for group members: realm={0} attrName={1} attrValue={2}", realm.getId(), attrName, attrValue);
        return Stream.empty();
    }
    
    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult,
        Integer maxResults)
    {
        log.infov("search for group members with params: realm={0} groupId={1} firstResult={2} maxResults={3}", realm.getId(), group.getId(), firstResult, maxResults);
        return Stream.empty();
    }
    
    private Stream<UserModel> internalSearchForUser(String search, RealmModel realm, PagingUtil.Pageable pageable) {
        return toUserModel(realm, repository.findUsers(search, pageable));
    }
    
    @Override
    public UserModel addUser(RealmModel realm, String username) {
        // from documentation: "If your provider has a configuration switch to turn off adding a user, returning null from this method will skip the provider and call the next one."
        return null;
    }
    
    
    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        boolean userRemoved = repository.removeUser();
        
        if (userRemoved) {
            log.infov("deleted keycloak user: realm={0} userId={1} username={2}", realm.getId(), user.getId(), user.getUsername());
        }
        
        return userRemoved;
    }

    public void unlinkUser(RealmModel realm, String userId) {
        log.infov("Attempting to unlink user: realmId={0} userId={1}", realm.getId(), userId);
        UserModel user = session.users().getUserById(realm, userId);

        if (user != null) {
            if (user.getFederationLink() != null && user.getFederationLink().equals(model.getId())) {
                user.setFederationLink(null);
                log.infov("User unlinked: realmId={0} userId={1}", realm.getId(), userId);
            } else {
                log.warnv("User does not have a matching federation link: realmId={0} userId={1} federationLink={2}", realm.getId(), userId, user.getFederationLink());
            }
        } else {
            log.warnv("User not found for unlinking: realmId={0} userId={1}", realm.getId(), userId);
        }
    }

    @Override
    public SynchronizationResult sync(KeycloakSessionFactory sessionFactory) {
        log.infov("Sync called. Sync enabled: {0}", syncEnabled);
        if (!syncEnabled) {
            return SynchronizationResult.empty();
        }

        log.info("Starting user synchronization...");
        SynchronizationResult result = SynchronizationResult.empty();
        List<Map<String, String>> usersFromDb = repository.getAllUsersForSync(); // This method needs to be created in UserRepository

        for (Map<String, String> dbUserMap : usersFromDb) {
            String username = dbUserMap.get("username");
            if (username == null) {
                log.warnv("User from DB is missing username: {0}", dbUserMap);
                result.increaseFailed();
                continue;
            }

            RealmModel realm = session.realms().getRealm(model.getParentId());
            UserModel keycloakUser = this.getUserByUsername(realm, username);

            if (keycloakUser == null) {
                log.infov("User {0} not found in Keycloak, creating...", username);
                keycloakUser = this.addUser(realm, username);
                if (keycloakUser == null) {
                    log.errorv("Failed to add user {0} to Keycloak.", username);
                    result.increaseFailed();
                    continue;
                }
                keycloakUser.setFederationLink(model.getId());
                mapUserAttributes(keycloakUser, dbUserMap);
                result.increaseAdded();
                log.infov("User {0} created in Keycloak.", username);
            } else {
                if (allowDatabaseToOverwriteKeycloak) {
                    log.infov("User {0} found in Keycloak, updating attributes...", username);
                    if (!model.getId().equals(keycloakUser.getFederationLink())) {
                        keycloakUser.setFederationLink(model.getId());
                    }
                    mapUserAttributes(keycloakUser, dbUserMap);
                    result.increaseUpdated();
                    log.infov("User {0} updated in Keycloak.", username);
                } else {
                    log.infov("User {0} found in Keycloak, but overwrite is disabled.", username);
                }
            }
        }
        log.info("User synchronization complete.");
        return result;
    }

    private void mapUserAttributes(UserModel keycloakUser, Map<String, String> dbUserMap) {
        keycloakUser.setEmail(dbUserMap.get("email"));
        keycloakUser.setFirstName(dbUserMap.get("firstName"));
        keycloakUser.setLastName(dbUserMap.get("lastName"));
        // Add any other attribute mappings here if needed
    }

    @Override
    public SynchronizationResult syncSince(java.util.Date lastSync, KeycloakSessionFactory sessionFactory) {
        log.infov("SyncSince called. Last sync: {0}, Sync enabled: {1}", lastSync, syncEnabled);
        // For now, just call the full sync method.
        // Future enhancements could fetch only updated users from the DB.
        return sync(sessionFactory);
    }
}
