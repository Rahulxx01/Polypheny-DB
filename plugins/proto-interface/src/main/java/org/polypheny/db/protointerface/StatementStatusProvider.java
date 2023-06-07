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

import static org.polypheny.db.protointerface.utils.ProtoUtils.createStatus;

import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import org.polypheny.db.protointerface.proto.StatementStatus;
import org.polypheny.db.protointerface.statements.ProtoInterfaceStatement;

public class StatementStatusProvider implements Runnable {
    private static final long UPDATE_INTERVAL = 2000;
    private int updateIntervall;
    private ProtoInterfaceStatement protoInterfaceStatement;
    private StreamObserver<StatementStatus> responseObserver;

    public StatementStatusProvider(ProtoInterfaceStatement statement, StreamObserver<StatementStatus> responseObserver) {
        this.protoInterfaceStatement = statement;
        this.responseObserver = responseObserver;
    }

    @SneakyThrows
    @Override
    public void run() {
        while ( !Thread.interrupted() ) {
            responseObserver.onNext(createStatus( protoInterfaceStatement ));
            Thread.sleep( UPDATE_INTERVAL );
        }
    }
}
