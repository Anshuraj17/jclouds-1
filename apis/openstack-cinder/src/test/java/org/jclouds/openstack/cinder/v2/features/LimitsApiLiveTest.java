/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.openstack.cinder.v2.features;

import com.google.common.collect.Iterables;
import org.jclouds.openstack.cinder.v1.internal.BaseCinderApiLiveTest;
import org.jclouds.openstack.cinder.v2.CinderApi;
import org.jclouds.openstack.cinder.v2.domain.Limits;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Test(groups = "live", testName = "LimitsApiLiveTest", singleThreaded = true)
public class LimitsApiLiveTest extends BaseCinderApiLiveTest<CinderApi> {

    private LimitsApi limitsApi;

    public LimitsApiLiveTest() {
        super("openstack-cinder-v2");
    }

    @BeforeClass(groups = {"integration", "live"})
    public void setupContext() {
        super.setup();
        String zone = Iterables.getFirst(api.getConfiguredZones(), "nova");
        limitsApi = api.getLimitsApi(zone);
    }

    public void testGetLimits() {
        Limits limits = limitsApi.getLimits();
        Assert.assertNotNull(limits);
        Assert.assertNotNull(limits.getAbsoluteLimit());
        assertTrue(limits.getAbsoluteLimit().getMaxTotalVolumeGigabytes() > 0);
        assertTrue(limits.getAbsoluteLimit().getTotalBackupGigabytesUsed() > 0);
        assertTrue(limits.getAbsoluteLimit().getMaxTotalVolumes() > 0);
        assertTrue(limits.getAbsoluteLimit().getTotalVolumesUsed() > 0);
        assertTrue(limits.getAbsoluteLimit().getMaxTotalSnapshots() > 0);
    }
}
