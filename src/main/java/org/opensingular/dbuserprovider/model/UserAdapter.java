package org.opensingular.dbuserprovider.model;

import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel; // Import UserModel
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.Collections;
import java.util.HashMap; // Import HashMap
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JBossLog
public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private final String keycloakId;
    private String username;
    private final Map<String, List<String>> attributesFromData; // Store attributes from constructor

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, Map<String, String> data, boolean allowDatabaseToOverwriteKeycloak) {
        super(session, realm, model);
        this.keycloakId = StorageId.keycloakId(model, data.get("id"));
        this.username = data.get("username");
        this.attributesFromData = new HashMap<>();

        // Initialize attributes directly from the data map
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                this.attributesFromData.put(entry.getKey(), Collections.singletonList(StringUtils.trimToNull(entry.getValue())));
            }
        }

        // The original logic for merging with existing attributes if needed (during sync)
        // For a newly created adapter directly from DB data, this.getAttributes() from super
        // might be problematic if the user isn't "fully" in Keycloak's context yet.
        // The syncUser method in DBUserStorageProvider should handle setting attributes on the *local* Keycloak user.
        // This adapter primarily represents the DB state.
        log.debugv("UserAdapter created for username: {0} with attributes: {1}", this.username, this.attributesFromData);
    }


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
        this.attributesFromData.put(UserModel.USERNAME, Collections.singletonList(username)); // Keep attributes map in sync
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

    // Override attribute methods to use attributesFromData as the source of truth
    // for this adapter, falling back to super.getAttributes() if needed,
    // or deciding how to merge/prioritize.

    @Override
    public Map<String, List<String>> getAttributes() {
        // Return a mutable copy if further modifications are expected by callers,
        // or an immutable one if this adapter's state is fixed after creation.
        // For now, let's return a copy of what we have.
        // Super.getAttributes() might try to load from federated storage, which could be what we want to avoid initially.
        Map<String, List<String>> allAttributes = new HashMap<>(super.getAttributes()); // Get attributes from federated storage
        allAttributes.putAll(this.attributesFromData); // Overlay with data from DB
        return allAttributes;
    }

    @Override
    public List<String> getAttribute(String name) {
        if (this.attributesFromData.containsKey(name)) {
            return this.attributesFromData.get(name);
        }
        return super.getAttribute(name);
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
        super.setSingleAttribute(name, value); // also update federated storage
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            this.attributesFromData.remove(name);
        } else {
            this.attributesFromData.put(name, values);
        }
        super.setAttribute(name, values); // also update federated storage
    }

    @Override
    public void removeAttribute(String name) {
        this.attributesFromData.remove(name);
        super.removeAttribute(name); // also update federated storage
    }
}
