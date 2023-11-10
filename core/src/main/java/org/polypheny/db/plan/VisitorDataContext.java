/*
 * Copyright 2019-2022 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.plan;


import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.logical.relational.LogicalFilter;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.snapshot.Snapshot;
import org.polypheny.db.nodes.Function;
import org.polypheny.db.nodes.Function.FunctionType;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.util.Pair;


/**
 * DataContext for evaluating an RexExpression
 */
@Slf4j
public class VisitorDataContext implements DataContext {

    private final Object[] values;


    public VisitorDataContext( Object[] values ) {
        this.values = values;
    }


    @Override
    public Snapshot getSnapshot() {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public JavaTypeFactory getTypeFactory() {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public QueryProvider getQueryProvider() {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public Object get( String name ) {
        if ( name.equals( "inputRecord" ) ) {
            return values;
        } else {
            return null;
        }
    }


    @Override
    public void addAll( Map<String, Object> map ) {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public Statement getStatement() {
        return null;
    }


    @Override
    public void addParameterValues( long index, AlgDataType type, List<PolyValue> data ) {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public AlgDataType getParameterType( long index ) {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public List<Map<Long, PolyValue>> getParameterValues() {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public void setParameterValues( List<Map<Long, PolyValue>> values ) {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public Map<Long, AlgDataType> getParameterTypes() {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    @Override
    public void setParameterTypes( Map<Long, AlgDataType> types ) {
        throw new UnsupportedOperationException( "This operation is not supported for " + getClass().getSimpleName() );
    }


    public static DataContext of( AlgNode targetRel, LogicalFilter queryRel ) {
        return of( targetRel.getRowType(), queryRel.getCondition() );
    }


    public static DataContext of( AlgDataType rowType, RexNode rex ) {
        final int size = rowType.getFieldList().size();
        final Object[] values = new Object[size];
        final List<RexNode> operands = ((RexCall) rex).getOperands();
        final RexNode firstOperand = operands.get( 0 );
        final RexNode secondOperand = operands.get( 1 );
        final Pair<Integer, ?> value = getValue( firstOperand, secondOperand );
        if ( value != null ) {
            int index = value.getKey();
            values[index] = value.getValue();
            return new VisitorDataContext( values );
        } else {
            return null;
        }
    }


    public static DataContext of( AlgDataType rowType, List<Pair<RexIndexRef, RexNode>> usageList ) {
        final int size = rowType.getFieldList().size();
        final Object[] values = new Object[size];
        for ( Pair<RexIndexRef, RexNode> elem : usageList ) {
            Pair<Integer, ?> value = getValue( elem.getKey(), elem.getValue() );
            if ( value == null ) {
                log.warn( "{} is not handled for {} for checking implication", elem.getKey(), elem.getValue() );
                return null;
            }
            int index = value.getKey();
            values[index] = value.getValue();
        }
        return new VisitorDataContext( values );
    }


    public static Pair<Integer, ?> getValue( RexNode inputRef, RexNode literal ) {
        inputRef = removeCast( inputRef );
        literal = removeCast( literal );

        if ( inputRef instanceof RexIndexRef && literal instanceof RexLiteral ) {
            final int index = ((RexIndexRef) inputRef).getIndex();
            final RexLiteral rexLiteral = (RexLiteral) literal;
            final AlgDataType type = inputRef.getType();

            if ( type.getPolyType() == null ) {
                log.warn( "{} returned null PolyType", inputRef );
                return null;
            }

            switch ( type.getPolyType() ) {
                case INTEGER:
                    return Pair.of( index, rexLiteral.getValue() );
                case DOUBLE:
                    return Pair.of( index, rexLiteral.getValue() );
                case REAL:
                    return Pair.of( index, rexLiteral.getValue() );
                case BIGINT:
                    return Pair.of( index, rexLiteral.getValue() );
                case SMALLINT:
                    return Pair.of( index, rexLiteral.getValue() );
                case TINYINT:
                    return Pair.of( index, rexLiteral.getValue() );
                case DECIMAL:
                    return Pair.of( index, rexLiteral.getValue() );
                case DATE:
                case TIME:
                    return Pair.of( index, rexLiteral.getValue() );
                case TIMESTAMP:
                    return Pair.of( index, rexLiteral.getValue() );
                case CHAR:
                    return Pair.of( index, rexLiteral.getValue() );
                case VARCHAR:
                    return Pair.of( index, rexLiteral.getValue() );
                default:
                    // TODO: Support few more supported cases
                    log.warn( "{} for value of class {} is being handled in default way", type.getPolyType(), rexLiteral.getValue().getClass() );
                    if ( rexLiteral.getValue().isString() ) {
                        return Pair.of( index, rexLiteral.getValue().asString().value );
                    } else {
                        return Pair.of( index, rexLiteral.getValue() );
                    }
            }
        }

        // Unsupported Arguments
        return null;
    }


    private static RexNode removeCast( RexNode inputRef ) {
        if ( inputRef instanceof RexCall ) {
            final RexCall castedRef = (RexCall) inputRef;
            final Operator operator = castedRef.getOperator();
            if ( ((Function) operator).getFunctionType() == FunctionType.CAST ) {
                inputRef = castedRef.getOperands().get( 0 );
            }
        }
        return inputRef;
    }

}

