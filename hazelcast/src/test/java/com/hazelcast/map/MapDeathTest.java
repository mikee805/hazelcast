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

package com.hazelcast.map;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.StaticNodeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(com.hazelcast.util.RandomBlockJUnit4ClassRunner.class)
public class MapDeathTest  {

    @Test
    public void testMapReleaseLocks() throws InterruptedException {
        StaticNodeFactory factory = new StaticNodeFactory(2);
        HazelcastInstance instance1 = factory.newHazelcastInstance(null);
        IMap map = instance1.getMap("testMapReleaseLocks");
        int size = 1000;
        for (int i = 0; i < size; i++) {
            map.put(i, i);
            map.lock(i);
        }

        HazelcastInstance instance2 = factory.newHazelcastInstance(null);

        for (int i = 0; i < size; i++) {
            assertEquals(true, map.isLocked(i));
        }
        Thread.sleep(100);
        instance1.getLifecycleService().shutdown();
        IMap map2 = instance2.getMap("testMapReleaseLocks");

        Thread.sleep(3000);
        for (int i = 0; i < size; i++) {
            assertEquals(false, map2.isLocked(i));
        }
        assertEquals(map2.size(), size);
        Hazelcast.shutdownAll();
    }

}
