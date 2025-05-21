package org.opensingular.dbuserprovider.model;

import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel; 
import org.keycloak.models.UserCredentialManager;
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

    private final String keycloakId;
    private String username;
    private final KeycloakSession session;
    private final RealmModel realm;
    private final ComponentModel model;
    private final Map<String, List<String>> attributesFromData; 
    
    @Override
    public UserCredentialManager credentialManager() {
        return session.userCredentialManager();
    }

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
        String timestamp = getFirstAttribute(UserModel.CREATED_TIMESTAMP);
        return timestamp != null ? Long.valueOf(timestamp) : null;
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        if (timestamp == null) {
            removeAttribute(UserModel.CREATED_TIMESTAMP);
        } else {
            setSingleAttribute(UserModel.CREATED_TIMESTAMP, timestamp.toString());
        }
    }
}
