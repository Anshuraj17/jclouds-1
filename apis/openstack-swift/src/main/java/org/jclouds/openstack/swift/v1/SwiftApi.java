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
package org.jclouds.openstack.swift.v1;

import java.io.Closeable;
import java.util.Set;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.jclouds.javax.annotation.Nullable;
import org.jclouds.location.Region;
import org.jclouds.location.functions.RegionToEndpoint;
import org.jclouds.openstack.swift.v1.features.AccountApi;
import org.jclouds.openstack.swift.v1.features.BulkApi;
import org.jclouds.openstack.swift.v1.features.ContainerApi;
import org.jclouds.openstack.swift.v1.features.ObjectApi;
import org.jclouds.openstack.swift.v1.features.StaticLargeObjectApi;
import org.jclouds.rest.annotations.Delegate;
import org.jclouds.rest.annotations.EndpointParam;

import com.google.common.annotations.Beta;
import com.google.inject.Provides;

/**
 * Provides access to the OpenStack Object Storage (Swift) API.
 * <p/>
 * OpenStack Object Storage is an object-based storage system that stores content and metadata
 * as objects. You create, modify, and get objects and metadata using this API.
 * <p/>
 * This API is new to jclouds and hence is in Beta. That means we need people to use it and give us feedback. Based
 * on that feedback, minor changes to the interfaces may happen. This code will replace
 * {@code org.jclouds.openstack.swift.SwiftClient} in jclouds 2.0 and it is recommended you adopt it sooner than later.
 */
@Beta
public interface SwiftApi extends Closeable {

   @Provides
   @Region
   Set<String> getConfiguredRegions();

   @Delegate
   AccountApi getAccountApiForRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   @Delegate
   BulkApi getBulkApiForRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   @Delegate
   ContainerApi getContainerApiForRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   @Delegate
   @Path("/{containerName}")
   ObjectApi getObjectApiForRegionAndContainer(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region,
                                               @PathParam("containerName") String containerName);

   @Delegate
   @Path("/{containerName}")
   StaticLargeObjectApi getStaticLargeObjectApiForRegionAndContainer(
         @EndpointParam(parser = RegionToEndpoint.class) @Nullable String region,
         @PathParam("containerName") String containerName);

   /**
    * @deprecated Please use {@link #getConfiguredRegions()} as this method will be removed in jclouds 1.8.
    */
   @Deprecated
   @Provides
   @Region
   Set<String> configuredRegions();

   /**
    * @deprecated Please use {@link #getAccountApiForRegion(String)} as this method will be removed in jclouds 1.8.
    */
   @Deprecated
   @Delegate
   AccountApi accountApiInRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   /**
    * @deprecated Please use {@link #getBulkApiForRegion(String)} as this method will be removed in jclouds 1.8.
    */
   @Deprecated
   @Delegate
   BulkApi bulkApiInRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   /**
    * @deprecated Please use {@link #getContainerApiForRegion(String)} as this method will be removed in
    *             jclouds 1.8.
    */
   @Deprecated
   @Delegate
   ContainerApi containerApiInRegion(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region);

   /**
    * @deprecated Please use {@link #getObjectApiForRegionAndContainer(String, String)} as this method will be
    *             removed in jclouds 1.8.
    */
   @Deprecated
   @Delegate
   @Path("/{containerName}")
   ObjectApi objectApiInRegionForContainer(@EndpointParam(parser = RegionToEndpoint.class) @Nullable String region,
                                           @PathParam("containerName") String containerName);

   /**
    * @deprecated Please use {@link #getStaticLargeObjectApiForRegionAndContainer(String, String)} as this method
    *             will be removed in jclouds 1.8.
    */
   @Deprecated
   @Delegate
   @Path("/{containerName}")
   StaticLargeObjectApi staticLargeObjectApiInRegionForContainer(
         @EndpointParam(parser = RegionToEndpoint.class) @Nullable String region,
         @PathParam("containerName") String containerName);
}
