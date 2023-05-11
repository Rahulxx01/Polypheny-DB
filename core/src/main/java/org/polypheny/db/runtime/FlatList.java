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

package org.polypheny.db.runtime;


import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import lombok.experimental.Delegate;
import lombok.experimental.NonFinal;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;


/**
 * Space-efficient, comparable, immutable lists.
 */
@NonFinal // todo dl remove when own Immutable impls are completely removed...
public class FlatList<T extends Comparable<T>> implements Comparable<FlatList<T>>, List<T> {

    @Delegate
    List<T> list;


    public FlatList( List<T> list ) {
        this.list = list;
    }


    @SafeVarargs
    public static <T extends Comparable<T>> FlatList<T> of( T... elements ) {
        return new FlatList<>( Arrays.asList( elements ) );
    }



    public static <T extends Comparable<T>> FlatList<T> copyOf( Iterator<T> list ) {
        return new FlatList<>( Lists.newArrayList( list ) );
    }


    public static <T extends Comparable<T>> FlatList<T> copyOf( Collection<T> list ) {
        return FlatList.copyOf( list.iterator() );
    }


    @Override
    public int compareTo( @NotNull FlatList<T> other ) {
        if ( size() != other.size() ) {
            return size() > other.size() ? 1 : -1;
        }
        for ( int i = 0; i < size(); i++ ) {
            Comparable<T> o0 = get( i );
            Comparable<T> o1 = other.get( i );
            int c = Objects.compare( o0, o1, ( a, b ) -> a.compareTo( (T) b ) );
            if ( c != 0 ) {
                return c;
            }
        }

        return 0;
    }


}

