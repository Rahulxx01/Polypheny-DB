/*
 * Copyright 2019-2023 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.protointerface;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.iface.AuthenticationException;
import org.polypheny.db.iface.Authenticator;
import org.polypheny.db.protointerface.proto.ConnectionRequest;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.transaction.TransactionManager;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class ClientManager {
    private static final long HEARTBEAT_TOLERANCE = 2000;
    @Getter
    private long heartbeatInterval;

    private ConcurrentHashMap<String, PIClient> openConnections;
    private final Authenticator authenticator;
    private final TransactionManager transactionManager;
    private Timer cleanupTimer;

    public ClientManager(PIPlugin.ProtoInterface protoInterface) {
        this.openConnections = new ConcurrentHashMap<>();
        this.authenticator = protoInterface.getAuthenticator();
        this.transactionManager = protoInterface.getTransactionManager();
        if (protoInterface.isRequiresHeartbeat()) {
            this.heartbeatInterval = protoInterface.getHeartbeatIntervall();
            this.cleanupTimer = new Timer();
            cleanupTimer.schedule(createNewCleanupTask(), 0, heartbeatInterval + HEARTBEAT_TOLERANCE);
        }
        this.heartbeatInterval = 0;
    }


    public void unregisterConnection(PIClient client) {
        synchronized (client) {
            client.prepareForDisposal();
            openConnections.remove(client.getClientUUID());
        }
    }


    public void registerConnection(ConnectionRequest connectionRequest) throws AuthenticationException, TransactionException, PIServiceException {
        if (log.isTraceEnabled()) {
            log.trace("User {} tries to establish connection via proto interface.", connectionRequest.getClientUuid());
        }
        // reject already connected user
        if (isConnected(connectionRequest.getClientUuid())) {
            throw new PIServiceException("A user with uid " + connectionRequest.getClientUuid() + "is already connected.");
        }
        String username;
        if (!connectionRequest.hasUsername()) {
            username = Catalog.USER_NAME;
        }
        PIClientProperties properties = getPropertiesOrDefault(connectionRequest);
        String password = connectionRequest.hasPassword() ? connectionRequest.getPassword() : null;
        final CatalogUser user = authenticateUser(connectionRequest.getUsername(), password);
        Transaction transaction = transactionManager.startTransaction(user, null, false, "proto-interface");
        LogicalNamespace namespace;
        if (properties.haveNamespaceName()) {
            try {
                namespace = Catalog.getInstance().getSnapshot().getNamespace( properties.getNamespaceName() );
            } catch (Exception e) {
                throw new PIServiceException("Getting namespace " + properties.getNamespaceName() + " failed.");
            }
        } else {
            namespace = Catalog.getInstance().getSnapshot().getNamespace(Catalog.defaultNamespaceName);
        }
        assert namespace != null;
        transaction.commit();
        properties.updateNamespaceName(namespace.getName());
        PIClient client = PIClient.newBuilder()
                .setClientUUID(connectionRequest.getClientUuid())
                .setTransactionManager(transactionManager)
                .setCatalogUser(user)
                .setLogicalNamespace(namespace)
                .setClientProperties(properties)
                .build();
        openConnections.put(connectionRequest.getClientUuid(), client);
        if (log.isTraceEnabled()) {
            log.trace("proto-interface established connection to user {}.", connectionRequest.getClientUuid());
        }
    }

    private PIClientProperties getPropertiesOrDefault(ConnectionRequest connectionRequest) {
        if (connectionRequest.hasConnectionProperties()) {
            return new PIClientProperties(connectionRequest.getConnectionProperties());
        }
        return PIClientProperties.getDefaultInstance();
    }


    public PIClient getClient(String clientUUID) throws PIServiceException {
        if (!openConnections.containsKey(clientUUID)) {
            throw new PIServiceException("Client not registered! Has the server been restarted in the meantime?");
        }
        return openConnections.get(clientUUID);
    }


    private CatalogUser authenticateUser(String username, String password) throws AuthenticationException {
        return authenticator.authenticate(username, password);
    }

    private TimerTask createNewCleanupTask() {
        Runnable runnable = this::unregisterInactiveClients;
        return new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }
        };
    }

    private void unregisterInactiveClients() {
        List<PIClient> inactiveClients = openConnections.values().stream()
                .filter(c -> !c.returnAndResetIsActive()).collect(Collectors.toList());
        inactiveClients.forEach(this::unregisterConnection);
    }


    private boolean isConnected(String clientUUID) {
        return openConnections.containsKey(clientUUID);
    }

}
