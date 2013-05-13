/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.deprecated.map.client;

import com.hazelcast.map.MapService;
import com.hazelcast.map.proxy.DataMapProxy;
import com.hazelcast.nio.serialization.Data;

import java.util.concurrent.TimeUnit;

public class MapPutIfAbsentHandler extends MapCommandHandlerWithTTL {

    public MapPutIfAbsentHandler(MapService mapService) {
        super(mapService);
    }

    @Override
    protected Data processMapOp(DataMapProxy dataMapProxy, Data key, Data value, long ttl) {
        Data oldValue;
        if (ttl <= 0) {
            oldValue = dataMapProxy.putIfAbsent(key, value);
        } else {
            oldValue = dataMapProxy.putIfAbsent(key, value, ttl, TimeUnit.MILLISECONDS);
        }
        return oldValue;
    }
}