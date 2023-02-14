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

package org.polypheny.db.adaptimizer.polyfierconnect;

import lombok.AccessLevel;
import lombok.Setter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.constant.ExplainFormat;
import org.polypheny.db.algebra.constant.ExplainLevel;
import org.polypheny.db.plan.AlgOptUtil;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

@Setter(AccessLevel.PRIVATE)
public class PolyfierResult implements Serializable {

    public String logical;
    public String physical;
    public String message;
    public String cause;
    public String result;
    public Long seed;
    public Long actual;
    public Long predicted;
    public boolean success;

    public boolean wasSuccess() {
        return this.success;
    }

    public PolyfierResult( PolyfierQueryExecutor.PolyfierQueryResult polyfierQueryResult ) {
        polyfierQueryResult.getLogical().ifPresentOrElse(
                node -> setLogical( formatLogicalPlan( node ) ),
                () -> setLogical( null )
        );
        polyfierQueryResult.getPhysical().ifPresentOrElse(
                node -> setPhysical( formatPhysicalPlan( node ) ),
                () -> setPhysical( null )
        );
        polyfierQueryResult.getMessage().ifPresentOrElse(
                this::setMessage,
                () -> setMessage( null )
        );
        polyfierQueryResult.getCause().ifPresentOrElse(
                e -> setCause( formatCause( e ) ),
                () -> setCause( null )
        );
        polyfierQueryResult.getActualTime().ifPresentOrElse(
                this::setActual,
                () -> setActual( null )
        );
        polyfierQueryResult.getPredictedTime().ifPresentOrElse(
                this::setPredicted,
                () -> setPredicted( null )
        );
        polyfierQueryResult.getSeed().ifPresentOrElse(
                this::setSeed,
                () -> setSeed( null )
        );
        polyfierQueryResult.getResultSet().ifPresentOrElse(
                resultSet -> setResult( formatResultSet( resultSet ) ),
                () -> setResult( null )
        );
    }

    private String formatLogicalPlan( AlgNode node ) {
        return AlgOptUtil.dumpPlan("logical", node, ExplainFormat.JSON, ExplainLevel.ALL_ATTRIBUTES );
    }

    private String formatPhysicalPlan( AlgNode node ) {
        return AlgOptUtil.dumpPlan("logical", node, ExplainFormat.JSON, ExplainLevel.ALL_ATTRIBUTES );
    }

    private String formatCause( Throwable cause ) {
        return cause.getMessage();
    }

    private String formatResultSet( List<List<Object>> resultSet ) {
        List<String> result = new LinkedList<>();
        for ( List<Object> row : resultSet ) {
            row.stream().map( Object::toString ).forEach( result::add );
        }
        return result.toString();
    }


}
