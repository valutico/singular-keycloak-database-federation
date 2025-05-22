package org.opensingular.dbuserprovider;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.cache.CachedUserModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
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
                                              UserLookupProvider, UserQueryProvider, CredentialInputUpdater, CredentialInputValidator, UserRegistrationProvider {
    
    private final KeycloakSession session;
    private final ComponentModel  model;
    private final UserRepository  repository; // Made final
    private final boolean allowDatabaseToOverwriteKeycloak;
    private final boolean syncNewUsersOnLogin;

    // Constructor now accepts UserRepository
    DBUserStorageProvider(KeycloakSession session, ComponentModel model, UserRepository repository, QueryConfigurations queryConfigurations) {
        this.session    = session;
        this.model      = model;
        this.repository = repository; // Use the passed-in repository
        this.allowDatabaseToOverwriteKeycloak = queryConfigurations.getAllowDatabaseToOverwriteKeycloak();
        this.syncNewUsersOnLogin = queryConfigurations.getSyncNewUsersOnLogin();
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
        boolean isValid = repository.validateCredentials(dbUser.getUsername(), cred.getChallengeResponse());

        if (isValid) {
            UserModel localUser = session.users().getUserByUsername(realm, dbUser.getUsername());
            if (localUser == null) {
                if (this.syncNewUsersOnLogin) {
                    log.infov("User {0} not found locally, creating as syncNewUsersOnLogin is true...", dbUser.getUsername());
                    localUser = session.users().addUser(realm, dbUser.getUsername());
                    localUser.setEnabled(true);
                    localUser.setEmail(dbUser.getEmail());
                    localUser.setFirstName(dbUser.getFirstName());
                    localUser.setLastName(dbUser.getLastName());

                    if (dbUser instanceof UserAdapter) {
                        UserAdapter dbUserAdapter = (UserAdapter) dbUser;
                        for (Map.Entry<String, List<String>> entry : dbUserAdapter.getAttributes().entrySet()) {
                            // Avoid overwriting essential or protected attributes
                            if (!entry.getKey().equalsIgnoreCase(UserModel.USERNAME) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.EMAIL) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.FIRST_NAME) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.LAST_NAME)) {
                                localUser.setAttribute(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    localUser.setFederationLink(model.getId());
                    log.infov("User {0} created locally and linked to provider {1}", localUser.getUsername(), model.getId());
                } else {
                    log.infov("User {0} not found locally and syncNewUsersOnLogin is false, login failed.", dbUser.getUsername());
                    // If sync is off and user is not local, isValid should effectively be false for this provider.
                    // However, the external DB already validated the password.
                    // This means an external user authenticated, but we are choosing not to import them.
                    // Keycloak will then proceed to other providers or ultimately fail the login if no provider can establish a local user.
                    // Returning false here would be misleading as the credential WAS valid against the external DB.
                    // The decision not to import is a policy of this provider instance.
                }
            } else {
                log.infov("User {0} found locally.", dbUser.getUsername());
                if (allowDatabaseToOverwriteKeycloak && model.getId().equals(localUser.getFederationLink())) {
                    log.infov("Updating user {0} from database.", dbUser.getUsername());
                    localUser.setEmail(dbUser.getEmail());
                    localUser.setFirstName(dbUser.getFirstName());
                    localUser.setLastName(dbUser.getLastName());
                    if (dbUser instanceof UserAdapter) {
                        UserAdapter dbUserAdapter = (UserAdapter) dbUser;
                        for (Map.Entry<String, List<String>> entry : dbUserAdapter.getAttributes().entrySet()) {
                            // Avoid overwriting essential or protected attributes
                            if (!entry.getKey().equalsIgnoreCase(UserModel.USERNAME) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.EMAIL) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.FIRST_NAME) &&
                                !entry.getKey().equalsIgnoreCase(UserModel.LAST_NAME)) {
                                localUser.setAttribute(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    log.infov("User {0} updated.", localUser.getUsername());
                } else {
                    log.infov("Not updating user {0}. allowDatabaseToOverwriteKeycloak={1}, federationLink={2}", dbUser.getUsername(), allowDatabaseToOverwriteKeycloak, localUser.getFederationLink());
                }
            }
        }
        return isValid;
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        
        if (user.getFederationLink() == null) {
            // User is local (unlinked)
            log.infov("Attempting local password update for unlinked user: {0}", user.getUsername());
            if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
                 return false;
            }
            UserCredentialModel cred = (UserCredentialModel) input;
            if (cred.getType() == null) {
                cred.setType(PasswordCredentialModel.TYPE);
            }
            if (cred.getValue() == null) {
                return false;
            }
            if (cred.getValue().isEmpty()) {
                return false;
            }
            if (cred.getType() == null) {
                cred.setType(PasswordCredentialModel.TYPE);
            }
            //update password in local storage
            session.userCredentialManager().updateCredential(realm, user, cred);

            return false;
            //return session.userCredentialManager().updateCredential(realm, user, input);
        } else {
            // User is federated, maintain existing behavior
            log.infov("Attempting credential update for federated user: realm={0} user={1}", realm.getId(), user.getUsername());
            
            if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
                return false;
            }
            
            UserCredentialModel cred = (UserCredentialModel) input;
            return repository.updateCredentials(user.getUsername(), cred.getChallengeResponse());
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

        UserModel localUser = session.users().getUserById(realm, id);
        if (localUser != null && localUser.getFederationLink() == null) {
            log.debugv("User found in Keycloak local storage and is not federated: userId={0}", id);
            return localUser;
        }
        
        String externalId = StorageId.externalId(id);
        Map<String, String> user = repository.findUserById(externalId);

        if (user == null) {
            log.debugv("findUserById returned null for externalId {0}, skipping creation of UserAdapter", externalId);
            return null;
        } else {
            return new UserAdapter(session, realm, model, user, allowDatabaseToOverwriteKeycloak);
        }
    }
    
    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        
        log.infov("lookup user by username: realm={0} username={1}", realm.getId(), username);

        UserModel localUser = session.users().getUserByUsername(realm, username);
        if (localUser != null && localUser.getFederationLink() == null) {
            log.debugv("User found in Keycloak local storage and is not federated: username={0}", username);
            return localUser;
        }
        
        return repository.findUserByUsername(username).map(u -> new UserAdapter(session, realm, model, u, allowDatabaseToOverwriteKeycloak)).orElse(null);
    }
    
    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        
        log.infov("lookup user by email: realm={0} email={1}", realm.getId(), email);

        UserModel localUser = session.users().getUserByEmail(realm, email);
        if (localUser != null && localUser.getFederationLink() == null) {
            log.debugv("User found in Keycloak local storage and is not federated: email={0}", email);
            return localUser;
        }
        
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
}
