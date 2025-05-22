package org.opensingular.dbuserprovider.model;

import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.GroupModel; // Added
import org.keycloak.models.RoleModel;  // Added
import org.keycloak.models.ClientModel; // Added
import org.keycloak.storage.StorageId;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JBossLog
public class UserAdapter implements UserModel {

    // Using a provider-specific key for the created timestamp attribute
    public static final String DB_CREATED_TIMESTAMP = "createdTimestamp";
    public static final String DB_SERVICE_ACCOUNT_CLIENT_LINK = "serviceAccountClientLink"; // For service account link

    private final String keycloakId;
    private String username;
    private final KeycloakSession session;
    private final RealmModel realm;
    private final ComponentModel model;
    private final Map<String, List<String>> attributesFromData;

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, Map<String, String> data, boolean allowDatabaseToOverwriteKeycloak) {
        this.session = session;
        this.realm = realm;
        this.model = model;
        this.keycloakId = StorageId.keycloakId(model, data.get("id"));
        this.username = data.get("username");
        this.attributesFromData = new HashMap<>();

        // Initialize attributes directly from the data map
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                this.attributesFromData.put(entry.getKey(), Collections.singletonList(StringUtils.trimToNull(entry.getValue())));
            }
        }

        log.debugv("UserAdapter created for username: {0} with attributes: {1}", this.username, this.attributesFromData);
    }

    // Basic UserModel methods
    @Override
    public String getId() {
        return keycloakId;
    }

    @Override
    public java.util.stream.Stream<RoleModel> getClientRoleMappingsStream(ClientModel app) {
        return getClientRoleMappings(app).stream();
    }

    @Override
    public org.keycloak.models.SubjectCredentialManager credentialManager() {
        return this;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
        this.attributesFromData.put(UserModel.USERNAME, Collections.singletonList(username));
    }

    @Override
    public String getFirstName() {
        return getFirstAttribute(UserModel.FIRST_NAME);
    }

    @Override
    public void setFirstName(String firstName) {
        setSingleAttribute(UserModel.FIRST_NAME, firstName);
    }

    @Override
    public String getLastName() {
        return getFirstAttribute(UserModel.LAST_NAME);
    }

    @Override
    public void setLastName(String lastName) {
        setSingleAttribute(UserModel.LAST_NAME, lastName);
    }

    @Override
    public String getEmail() {
        return getFirstAttribute(UserModel.EMAIL);
    }

    @Override
    public void setEmail(String email) {
        setSingleAttribute(UserModel.EMAIL, email);
    }
    
    @Override
    public boolean isEmailVerified() {
        return Boolean.parseBoolean(getFirstAttribute(UserModel.EMAIL_VERIFIED));
    }

    @Override
    public void setEmailVerified(boolean verified) {
        setSingleAttribute(UserModel.EMAIL_VERIFIED, String.valueOf(verified));
    }

    // Attribute methods
    @Override
    public Map<String, List<String>> getAttributes() {
        return new HashMap<>(this.attributesFromData);
    }

    @Override
    public List<String> getAttribute(String name) {
        if (this.attributesFromData.containsKey(name)) {
            return this.attributesFromData.get(name);
        }
        return Collections.emptyList();
    }
    
    @Override
    public String getFirstAttribute(String name) {
        List<String> list = getAttribute(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        if (value == null) {
            this.attributesFromData.remove(name);
        } else {
            this.attributesFromData.put(name, Collections.singletonList(value));
        }
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            this.attributesFromData.remove(name);
        } else {
            this.attributesFromData.put(name, values);
        }
    }

    @Override
    public void removeAttribute(String name) {
        this.attributesFromData.remove(name);
    }

    // Required methods for Keycloak 24
    @Override
    public Set<String> getRequiredActions() {
        return Collections.emptySet();
    }

    @Override
    public java.util.stream.Stream<String> getRequiredActionsStream() {
        return getRequiredActions().stream();
    }

    @Override
    public void addRequiredAction(String action) {
        // Not implemented - federated storage doesn't support this
    }

    @Override
    public void removeRequiredAction(String action) {
        // Not implemented - federated storage doesn't support this
    }

    @Override
    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public boolean isEnabled() {
        String enabled = getFirstAttribute(UserModel.ENABLED);
        return enabled == null || Boolean.parseBoolean(enabled);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setSingleAttribute(UserModel.ENABLED, String.valueOf(enabled));
    }

    @Override
    public Long getCreatedTimestamp() {
        String timestamp = getFirstAttribute(DB_CREATED_TIMESTAMP);
        return timestamp != null ? Long.valueOf(timestamp) : null;
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        if (timestamp == null) {
            removeAttribute(DB_CREATED_TIMESTAMP);
        } else {
            setSingleAttribute(DB_CREATED_TIMESTAMP, timestamp.toString());
        }
    }

    // Implementation for SubjectCredentialManager methods (UserModel extends SubjectCredentialManager)

    @Override
    public boolean updateCredential(org.keycloak.credential.CredentialInput input) {
        log.warnv("UserAdapter.updateCredential called for user {0}, input type {1}. Not supported.", getUsername(), input.getType());
        throw new UnsupportedOperationException("Credentials should be managed by the UserStorageProvider implementation.");
    }

    @Override
    public void updateCredentialDirectly(org.keycloak.models.UserCredentialModel cred) {
        log.warnv("UserAdapter.updateCredentialDirectly called for user {0}. Not supported.", getUsername());
        throw new UnsupportedOperationException("Credentials should be managed by the UserStorageProvider implementation.");
    }

    @Override
    public void updateCredentialDirectlyNoEvents(org.keycloak.models.UserCredentialModel cred) {
        log.warnv("UserAdapter.updateCredentialDirectlyNoEvents called for user {0}. Not supported.", getUsername());
        throw new UnsupportedOperationException("Credentials should be managed by the UserStorageProvider implementation.");
    }
    
    @Override
    public boolean isValid(List<org.keycloak.credential.CredentialInput> inputs) {
        log.warnv("UserAdapter.isValid called for user {0}. Not supported directly on adapter.", getUsername());
        throw new UnsupportedOperationException("Credential validation is handled by the UserStorageProvider implementation.");
    }

    @Override
    public boolean isConfiguredFor(String type) {
        log.debugv("UserAdapter.isConfiguredFor called for user {0}, type {1}. This is usually provider-dependent.", getUsername(), type);
        // This might need delegation to provider or a simpler check if only password is supported.
        return org.keycloak.models.credential.PasswordCredentialModel.TYPE.equals(type);
    }

    @Override
    public boolean isConfiguredLocally(String type) {
        // Federated users are not configured locally in this context
        return false;
    }

    @Override
    public java.util.stream.Stream<org.keycloak.models.UserCredentialModel> getStoredCredentialsStream() {
        return java.util.stream.Stream.empty(); // Federated users typically don't store credentials directly in Keycloak format here
    }

    @Override
    public java.util.stream.Stream<org.keycloak.models.UserCredentialModel> getStoredCredentialsByTypeStream(String type) {
        return java.util.stream.Stream.empty();
    }

    @Override
    public org.keycloak.models.UserCredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return null;
    }
    
    @Override
    public void removeStoredCredentialById(String id) {
        log.warnv("UserAdapter.removeStoredCredentialById called for user {0}, id {1}. Not supported.", getUsername(), id);
        throw new UnsupportedOperationException("Credentials should be managed by the UserStorageProvider implementation.");
    }

    @Override
    public java.util.stream.Stream<String> getStoredCredentialTypesStream() {
        // If only password is supported by the underlying DB storage.
        return java.util.stream.Stream.of(org.keycloak.models.credential.PasswordCredentialModel.TYPE);
    }

    // Other UserModel methods from Keycloak 24

    @Override
    public String getServiceAccountClientLink() {
        return getFirstAttribute(DB_SERVICE_ACCOUNT_CLIENT_LINK);
    }

    @Override
    public void setServiceAccountClientLink(String clientInternalId) {
        setSingleAttribute(DB_SERVICE_ACCOUNT_CLIENT_LINK, clientInternalId);
    }

    @Override
    public Set<GroupModel> getGroups() {
        // This provider doesn't support groups, return empty set
        return Collections.emptySet();
    }

    @Override
    public java.util.stream.Stream<GroupModel> getGroupsStream() {
        return getGroups().stream();
    }
    
    @Override
    public boolean isMemberOf(GroupModel group) {
        return false; // This provider doesn't support groups
    }
    
    @Override
    public String getFederationLink() {
        return getFirstAttribute("FEDERATION_LINK");
    }

    @Override
    public void setFederationLink(String link) {
        // This is important for allowing users to be unlinked or relinked if necessary
        setSingleAttribute("FEDERATION_LINK", link);
        if (link == null) { 
            removeAttribute("FEDERATION_LINK");
        }
    }

    // getAttributeStream is a default method in UserModel from Keycloak 17+, 
    // but if there's a need to override or if it was missing:
    @Override
    public java.util.stream.Stream<String> getAttributeStream(String name) {
        List<String> attributeValues = getAttribute(name);
        if (attributeValues == null) {
            return java.util.stream.Stream.empty();
        }
        return attributeValues.stream();
    }

    // org.keycloak.models.UserModel interface methods that might be missing if default implementations are not picked up
    // These might be provided as default methods in the interface in some Keycloak versions.
    // Adding them explicitly if they cause "does not override abstract method" errors.

    @Override
    public void joinGroup(GroupModel group) {
        // Not supported
        log.warnv("UserAdapter.joinGroup called for user {0}, group {1}. Not supported.", getUsername(), group.getName());
    }

    @Override
    public void leaveGroup(GroupModel group) {
        // Not supported
        log.warnv("UserAdapter.leaveGroup called for user {0}, group {1}. Not supported.", getUsername(), group.getName());
    }

    @Override
    public void addRequiredAction(UserModel.RequiredAction action) {
        addRequiredAction(action.name());
    }

    @Override
    public void removeRequiredAction(UserModel.RequiredAction action) {
        removeRequiredAction(action.name());
    }

    @Override
    public Set<RoleModel> getRealmRoleMappings() {
        return java.util.stream.Stream.<RoleModel>empty().collect(Collectors.toSet());
    }
    
    @Override
    public Set<RoleModel> getClientRoleMappings(ClientModel app) {
        return java.util.stream.Stream.<RoleModel>empty().collect(Collectors.toSet());
    }

    @Override
    public boolean hasRole(RoleModel role) {
        return false;
    }

    @Override
    public void grantRole(RoleModel role) {
        // Not supported
    }

    @Override
    public java.util.stream.Stream<RoleModel> getRoleMappingsStream() {
         return java.util.stream.Stream.empty();
    }
    
    @Override
    public java.util.stream.Stream<RoleModel> getRealmRoleMappingsStream() {
        return getRealmRoleMappings().stream();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        // Not supported for this federated provider
        log.warnv("UserAdapter.deleteRoleMapping called for user {0}, role {1}. Not supported.", getUsername(), role.getName());
    }

    // Methods from UserConsentProvider (UserModel extends UserConsentProvider.Streams)
    @Override
    public java.util.stream.Stream<org.keycloak.models.UserConsentModel> getConsentsStream(String clientId) {
        log.debugv("UserAdapter.getConsentsStream for clientId {0}", clientId);
        return java.util.stream.Stream.empty();
    }

    @Override
    public void addConsent(org.keycloak.models.UserConsentModel consent) {
        log.warnv("UserAdapter.addConsent called for user {0}, client {1}. Not supported.", getUsername(), consent.getClient().getClientId());
        // Not supported
    }

    @Override
    public org.keycloak.models.UserConsentModel getConsentByClient(String clientId) {
        log.debugv("UserAdapter.getConsentByClient for clientId {0}", clientId);
        return null;
    }

    @Override
    public void revokeConsentForClient(String clientId) {
        log.warnv("UserAdapter.revokeConsentForClient called for user {0}, client {1}. Not supported.", getUsername(), clientId);
        // Not supported
    }
}
