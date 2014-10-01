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
package org.jclouds.openstack.swift.v1.blobstore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.tryFind;
import static com.google.common.collect.Lists.transform;
import static org.jclouds.blobstore.options.ListContainerOptions.Builder.recursive;
import static org.jclouds.location.predicates.LocationPredicates.idEquals;
import static org.jclouds.openstack.swift.v1.options.PutOptions.Builder.metadata;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.blobstore.domain.MutableBlobMetadata;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.domain.internal.BlobBuilderImpl;
import org.jclouds.blobstore.domain.internal.BlobImpl;
import org.jclouds.blobstore.domain.internal.PageSetImpl;
import org.jclouds.blobstore.functions.BlobToHttpGetOptions;
import org.jclouds.blobstore.options.CreateContainerOptions;
import org.jclouds.blobstore.options.GetOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.blobstore.options.PutOptions;
import org.jclouds.blobstore.strategy.ClearListStrategy;
import org.jclouds.collect.Memoized;
import org.jclouds.domain.Location;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.ByteSourcePayload;
import org.jclouds.openstack.swift.v1.SwiftApi;
import org.jclouds.openstack.swift.v1.blobstore.functions.ToBlobMetadata;
import org.jclouds.openstack.swift.v1.blobstore.functions.ToListContainerOptions;
import org.jclouds.openstack.swift.v1.blobstore.functions.ToResourceMetadata;
import org.jclouds.openstack.swift.v1.domain.Container;
import org.jclouds.openstack.swift.v1.domain.ObjectList;
import org.jclouds.openstack.swift.v1.domain.SwiftObject;
import org.jclouds.openstack.swift.v1.features.ObjectApi;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;

public class RegionScopedSwiftBlobStore implements BlobStore {

   @Inject
   protected RegionScopedSwiftBlobStore(Injector baseGraph, BlobStoreContext context, SwiftApi api,
         @Memoized Supplier<Set<? extends Location>> locations, @Assisted String regionId) {
      checkNotNull(regionId, "regionId");
      Optional<? extends Location> found = tryFind(locations.get(), idEquals(regionId));
      checkArgument(found.isPresent(), "region %s not in %s", regionId, locations.get());
      this.region = found.get();
      this.regionId = regionId;
      this.toResourceMetadata = new ToResourceMetadata(found.get());
      this.context = context;
      this.api = api;
      // until we parameterize ClearListStrategy with a factory
      this.clearList = baseGraph.createChildInjector(new AbstractModule() {
         @Override
         protected void configure() {
            bind(BlobStore.class).toInstance(RegionScopedSwiftBlobStore.this);
         }
      }).getInstance(ClearListStrategy.class);
   }

   private final BlobStoreContext context;
   private final ClearListStrategy clearList;
   private final SwiftApi api;
   private final Location region;
   private final String regionId;
   private final BlobToHttpGetOptions toGetOptions = new BlobToHttpGetOptions();
   private final ToListContainerOptions toListContainerOptions = new ToListContainerOptions();
   private final ToResourceMetadata toResourceMetadata;

   @Override
   public Set<? extends Location> listAssignableLocations() {
      return ImmutableSet.of(region);
   }

   @Override
   public PageSet<? extends StorageMetadata> list() {
      // TODO: there may eventually be >10k containers..
      FluentIterable<StorageMetadata> containers = api.getContainerApiForRegion(regionId).list()
            .transform(toResourceMetadata);
      return new PageSetImpl<StorageMetadata>(containers, null);
   }

   @Override
   public boolean containerExists(String container) {
      Container val = api.getContainerApiForRegion(regionId).get(container);
      containerCache.put(container, Optional.fromNullable(val));
      return val != null;
   }

   @Override
   public boolean createContainerInLocation(Location location, String container) {
      return createContainerInLocation(location, container, CreateContainerOptions.NONE);
   }

   @Override
   public boolean createContainerInLocation(Location location, String container, CreateContainerOptions options) {
      checkArgument(location == null || location.equals(region), "location must be null or %s", region);
      if (options.isPublicRead()) {
         return api.getContainerApiForRegion(regionId).create(container, ANYBODY_READ);
      }
      return api.getContainerApiForRegion(regionId).create(container, BASIC_CONTAINER);
   }

   private static final org.jclouds.openstack.swift.v1.options.CreateContainerOptions BASIC_CONTAINER = new org.jclouds.openstack.swift.v1.options.CreateContainerOptions();
   private static final org.jclouds.openstack.swift.v1.options.CreateContainerOptions ANYBODY_READ = new org.jclouds.openstack.swift.v1.options.CreateContainerOptions()
         .anybodyRead();

   @Override
   public PageSet<? extends StorageMetadata> list(String container) {
      return list(container, ListContainerOptions.NONE);
   }

   @Override
   public PageSet<? extends StorageMetadata> list(final String container, ListContainerOptions options) {
      ObjectApi objectApi = api.getObjectApiForRegionAndContainer(regionId, container);
      ObjectList objects = objectApi.list(toListContainerOptions.apply(options));
      if (objects == null) {
         containerCache.put(container, Optional.<Container> absent());
         return new PageSetImpl<StorageMetadata>(ImmutableList.<StorageMetadata> of(), null);
      } else {
         containerCache.put(container, Optional.of(objects.getContainer()));
         List<? extends StorageMetadata> list = transform(objects, toBlobMetadata(container));
         int limit = Optional.fromNullable(options.getMaxResults()).or(10000);
         String marker = list.size() == limit ? list.get(limit - 1).getName() : null;
         // TODO: we should probably deprecate this option
         if (options.isDetailed()) {
            list = transform(list, new Function<StorageMetadata, StorageMetadata>() {
               @Override
               public StorageMetadata apply(StorageMetadata input) {
                  if (input.getType() != StorageType.BLOB) {
                     return input;
                  }
                  return blobMetadata(container, input.getName());
               }
            });
         }
         return new PageSetImpl<StorageMetadata>(list, marker);
      }
   }

   @Override
   public boolean blobExists(String container, String name) {
      return blobMetadata(container, name) != null;
   }

   @Override
   public String putBlob(String container, Blob blob) {
      return putBlob(container, blob, PutOptions.NONE);
   }

   @Override
   public String putBlob(String container, Blob blob, PutOptions options) {
      if (options.isMultipart()) {
         throw new UnsupportedOperationException();
      }
      ObjectApi objectApi = api.getObjectApiForRegionAndContainer(regionId, container);
      return objectApi.put(blob.getMetadata().getName(), blob.getPayload(), metadata(blob.getMetadata().getUserMetadata()));
   }

   @Override
   public BlobMetadata blobMetadata(String container, String name) {
      SwiftObject object = api.getObjectApiForRegionAndContainer(regionId, container).get(name);
      if (object == null) {
         return null;
      }
      return toBlobMetadata(container).apply(object);
   }

   @Override
   public Blob getBlob(String container, String key) {
      return getBlob(container, key, GetOptions.NONE);
   }

   @Override
   public Blob getBlob(String container, String name, GetOptions options) {
      ObjectApi objectApi = api.getObjectApiForRegionAndContainer(regionId, container);
      SwiftObject object = objectApi.get(name, toGetOptions.apply(options));
      if (object == null) {
         return null;
      }
      Blob blob = new BlobImpl(toBlobMetadata(container).apply(object));
      blob.setPayload(object.getPayload());
      blob.setAllHeaders(object.getHeaders());
      return blob;
   }

   @Override
   public void removeBlob(String container, String name) {
      api.getObjectApiForRegionAndContainer(regionId, container).delete(name);
   }

   @Override
   public BlobStoreContext getContext() {
      return context;
   }

   @Override
   public BlobBuilder blobBuilder(String name) {
      return new BlobBuilderImpl().name(name);
   }

   @Override
   public boolean directoryExists(String containerName, String directory) {
      return api.getObjectApiForRegionAndContainer(regionId, containerName)
            .get(directory) != null;
   }

   @Override
   public void createDirectory(String containerName, String directory) {
      api.getObjectApiForRegionAndContainer(regionId, containerName)
            .put(directory, directoryPayload);
   }

   private final Payload directoryPayload = new ByteSourcePayload(ByteSource.wrap(new byte[] {})) {
      {
         getContentMetadata().setContentType("application/directory");
      }
   };

   @Override
   public void deleteDirectory(String containerName, String directory) {
      api.getObjectApiForRegionAndContainer(regionId, containerName).delete(directory);
   }

   @Override
   public long countBlobs(String containerName) {
      Container container = api.getContainerApiForRegion(regionId).get(containerName);
      // undefined if container doesn't exist, so default to zero
      return container != null ? container.getObjectCount() : 0;
   }

   @Override
   public void clearContainer(String containerName) {
      clearContainer(containerName, recursive());
   }

   @Override
   public void clearContainer(String containerName, ListContainerOptions options) {
      // this could be implemented to use bulk delete
      clearList.execute(containerName, options);
   }

   @Override
   public void deleteContainer(String container) {
      clearContainer(container, recursive());
      api.getContainerApiForRegion(regionId).deleteIfEmpty(container);
      containerCache.invalidate(container);
   }

   @Override
   public boolean deleteContainerIfEmpty(String container) {
      boolean deleted = api.getContainerApiForRegion(regionId).deleteIfEmpty(container);
      if (deleted) {
         containerCache.invalidate(container);
      }
      return deleted;
   }

   protected final LoadingCache<String, Optional<Container>> containerCache = CacheBuilder.newBuilder().build(
         new CacheLoader<String, Optional<Container>>() {
            public Optional<Container> load(String container) {
               return Optional.fromNullable(api.getContainerApiForRegion(regionId).get(container));
            }
         });

   protected Function<SwiftObject, MutableBlobMetadata> toBlobMetadata(String container) {
      return new ToBlobMetadata(containerCache.getUnchecked(container).get());
   }

   @Override
   public long countBlobs(String containerName, ListContainerOptions options) {
      throw new UnsupportedOperationException();
   }
}
