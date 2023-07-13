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

package org.polypheny.db.protointerface.statements;

import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.languages.LanguageManager;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.protointerface.PIClient;
import org.polypheny.db.protointerface.PIStatementProperties;
import org.polypheny.db.protointerface.ProtoInterfaceServiceException;
import org.polypheny.db.protointerface.proto.PreparedStatement;
import org.polypheny.db.protointerface.proto.UnparameterizedStatement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class StatementManager {

    private final AtomicInteger statementIdGenerator;
    private Set<String> supportedLanguages;
    private PIClient protoInterfaceClient;
    private ConcurrentHashMap<Integer, PIStatement> openStatments;
    private ConcurrentHashMap<Integer, PIStatementBatch> openUnparameterizedBatches;


    public StatementManager(PIClient protoInterfaceClient) {
        this.protoInterfaceClient = protoInterfaceClient;
        statementIdGenerator = new AtomicInteger();
        openStatments = new ConcurrentHashMap<>();
        openUnparameterizedBatches = new ConcurrentHashMap<>();
        supportedLanguages = new HashSet<>();
    }


    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }


    public synchronized PIUnparameterizedStatement createUnparameterizedStatement(UnparameterizedStatement statement) {
        String languageName = statement.getStatementLanguageName();
        if (!isSupportedLanguage(languageName)) {
            throw new ProtoInterfaceServiceException("Language " + languageName + " not supported.");
        }
        final int statementId = statementIdGenerator.getAndIncrement();
        final PIUnparameterizedStatement interfaceStatement = PIUnparameterizedStatement.newBuilder()
                .setStatementId(statementId)
                .setQuery(statement.getStatement())
                .setQueryLanguage(QueryLanguage.from(languageName))
                .setProtoInterfaceClient(protoInterfaceClient)
                .setProperties(getPropertiesOrDefault(statement))
                .build();
        openStatments.put(statementId, interfaceStatement);
        if (log.isTraceEnabled()) {
            log.trace("created statement {}", interfaceStatement);
        }
        return interfaceStatement;
    }

    private PIStatementProperties getPropertiesOrDefault(UnparameterizedStatement unparameterizedStatement) {
        if (unparameterizedStatement.hasProperties()) {
            return new PIStatementProperties(unparameterizedStatement.getProperties());
        }
        return PIStatementProperties.getDefaultInstance();
    }

    private PIStatementProperties getPropertiesOrDefault(PreparedStatement preparedStatement) {
        if (preparedStatement.hasProperties()) {
            return new PIStatementProperties(preparedStatement.getProperties());
        }
        return PIStatementProperties.getDefaultInstance();
    }


    public synchronized PIUnparameterizedStatementBatch createUnparameterizedStatementBatch(List<UnparameterizedStatement> statements) {
        List<PIUnparameterizedStatement> PIUnparameterizedStatements = statements.stream()
                .map(this::createUnparameterizedStatement)
                .collect(Collectors.toList());
        final int batchId = statementIdGenerator.getAndIncrement();
        final PIUnparameterizedStatementBatch batch = new PIUnparameterizedStatementBatch(batchId, protoInterfaceClient, PIUnparameterizedStatements);
        openUnparameterizedBatches.put(batchId, batch);
        if (log.isTraceEnabled()) {
            log.trace("created batch {}", batch);
        }
        return batch;
    }


    public synchronized PIPreparedIndexedStatement createIndexedPreparedInterfaceStatement(PreparedStatement statement) {
        String languageName = statement.getStatementLanguageName();
        if (!isSupportedLanguage(languageName)) {
            throw new ProtoInterfaceServiceException("Language " + languageName + " not supported.");
        }
        final int statementId = statementIdGenerator.getAndIncrement();
        final PIPreparedIndexedStatement interfaceStatement = PIPreparedIndexedStatement.newBuilder()
                .setStatementId(statementId)
                .setProtoInterfaceClient(protoInterfaceClient)
                .setQuery(statement.getStatement())
                .setQueryLanguage(QueryLanguage.from(languageName))
                .setProperties(getPropertiesOrDefault(statement))
                .build();
        openStatments.put(statementId, interfaceStatement);
        if (log.isTraceEnabled()) {
            log.trace("created named prepared statement {}", interfaceStatement);
        }
        return interfaceStatement;
    }


    public synchronized PIPreparedNamedStatement createNamedPreparedInterfaceStatement(PreparedStatement statement) {
        String languageName = statement.getStatementLanguageName();
        if (!isSupportedLanguage(languageName)) {
            throw new ProtoInterfaceServiceException("Language " + languageName + " not supported.");
        }
        final int statementId = statementIdGenerator.getAndIncrement();
        final PIPreparedNamedStatement interfaceStatement = PIPreparedNamedStatement.newBuilder()
                .setStatementId(statementId)
                .setQuery(statement.getStatement())
                .setQueryLanguage(QueryLanguage.from(statement.getStatementLanguageName()))
                .setProperties(getPropertiesOrDefault(statement))
                .setProtoInterfaceClient(protoInterfaceClient)
                .build();
        openStatments.put(statementId, interfaceStatement);
        if (log.isTraceEnabled()) {
            log.trace("created named prepared statement {}", interfaceStatement);
        }
        return interfaceStatement;
    }

    public void closeAll() {
        openUnparameterizedBatches.values().forEach(this::closeBatch);
        openStatments.values().forEach(s -> closeStatement(s.getStatementId()));
    }


    public void closeBatch(PIStatementBatch toClose) {
        toClose.getStatements().forEach(s -> closeStatementOrBatch(s.getStatementId()));
    }



    private void closeStatement(int statementId) {
        PIStatement statementToClose = openStatments.remove(statementId);
        if (statementToClose == null) {
            return;
        }
        // TODO: implement closing of statements
    }


    public void closeStatementOrBatch(int statementId) {
        PIStatementBatch batchToClose = openUnparameterizedBatches.remove(statementId);
        if (batchToClose != null) {
            closeBatch(batchToClose);
            return;
        }
        closeStatement(statementId);
    }


    public PIStatement getStatement(int statementId) {
        PIStatement statement = openStatments.get(statementId);
        if (statement == null) {
            throw new ProtoInterfaceServiceException("A statement with id " + statementId + " does not exist for that client");
        }
        return statement;
    }


    public PIPreparedNamedStatement getNamedPreparedStatement(int statementId) {
        PIStatement statement = openStatments.get(statementId);
        if (statement == null) {
            throw new ProtoInterfaceServiceException("A statement with id " + statementId + " does not exist for that client");
        }
        if (!(statement instanceof PIPreparedNamedStatement)) {
            throw new ProtoInterfaceServiceException("A prepared statement with id " + statementId + " does not exist for that client");
        }
        return (PIPreparedNamedStatement) statement;
    }


    public PIPreparedIndexedStatement getIndexedPreparedStatement(int statementId) {
        PIStatement statement = openStatments.get(statementId);
        if (statement == null) {
            throw new ProtoInterfaceServiceException("A statement with id " + statementId + " does not exist for that client");
        }
        if (!(statement instanceof PIPreparedIndexedStatement)) {
            throw new ProtoInterfaceServiceException("A prepared statement with id " + statementId + " does not exist for that client");
        }
        return (PIPreparedIndexedStatement) statement;
    }


    public boolean isSupportedLanguage(String statementLanguageName) {
        return LanguageManager.getLanguages()
                .stream()
                .map(QueryLanguage::getSerializedName)
                .collect(Collectors.toSet())
                .contains(statementLanguageName);
    }

}
