/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity.mdns;

import static com.android.net.module.util.DnsUtils.equalsIgnoreDnsCase;
import static com.android.net.module.util.DnsUtils.toDnsUpperCase;
import static com.android.net.module.util.HandlerUtils.ensureRunningOnHandlerThread;
import static com.android.server.connectivity.mdns.MdnsResponse.EXPIRATION_NEVER;

import static java.lang.Math.min;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * The {@link MdnsServiceCache} manages the service which discovers from each socket and cache these
 * services to reduce duplicated queries.
 *
 * <p>This class is not thread safe, it is intended to be used only from the looper thread.
 *  However, the constructor is an exception, as it is called on another thread;
 *  therefore for thread safety all members of this class MUST either be final or initialized
 *  to their default value (0, false or null).
 */
public class MdnsServiceCache {
    public static class CacheKey {
        @NonNull final String mUpperCaseServiceType;
        @NonNull final SocketKey mSocketKey;

        CacheKey(@NonNull String serviceType, @NonNull SocketKey socketKey) {
            mUpperCaseServiceType = toDnsUpperCase(serviceType);
            mSocketKey = socketKey;
        }

        @Override public int hashCode() {
            return Objects.hash(mUpperCaseServiceType, mSocketKey);
        }

        @Override public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            return Objects.equals(mUpperCaseServiceType, ((CacheKey) other).mUpperCaseServiceType)
                    && Objects.equals(mSocketKey, ((CacheKey) other).mSocketKey);
        }

        @Override
        public String toString() {
            return "CacheKey{ ServiceType=" + mUpperCaseServiceType + ", " + mSocketKey + " }";
        }
    }

    public static class CachedService {
        @NonNull final MdnsResponse mService;
        boolean mServiceExpired;

        CachedService(MdnsResponse service) {
            mService = service;
            mServiceExpired = false;
        }
    }

    /**
     * A map of cached services. Key is composed of service type and socket. Value is the list of
     * services which are discovered from the given CacheKey.
     * When the MdnsFeatureFlags#NSD_EXPIRED_SERVICES_REMOVAL flag is enabled, the lists are sorted
     * by expiration time, with the earliest entries appearing first. This sorting allows the
     * removal process to progress through the expiration check efficiently.
     */
    @NonNull
    private final ArrayMap<CacheKey, List<CachedService>> mCachedServices = new ArrayMap<>();
    /**
     * A map of service expire callbacks. Key is composed of service type and socket and value is
     * the callback listener.
     */
    @NonNull
    private final ArrayMap<CacheKey, ServiceExpiredCallback> mCallbacks = new ArrayMap<>();
    @NonNull
    private final Handler mHandler;
    @NonNull
    private final MdnsFeatureFlags mMdnsFeatureFlags;
    @NonNull
    private final MdnsUtils.Clock mClock;
    private long mNextExpirationTime = EXPIRATION_NEVER;

    public MdnsServiceCache(@NonNull Looper looper, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this(looper, mdnsFeatureFlags, new MdnsUtils.Clock());
    }

    @VisibleForTesting
    MdnsServiceCache(@NonNull Looper looper, @NonNull MdnsFeatureFlags mdnsFeatureFlags,
            @NonNull MdnsUtils.Clock clock) {
        mHandler = new Handler(looper);
        mMdnsFeatureFlags = mdnsFeatureFlags;
        mClock = clock;
    }

    private List<MdnsResponse> cachedServicesToResponses(List<CachedService> cachedServices) {
        final List<MdnsResponse> responses = new ArrayList<>();
        for (CachedService cachedService : cachedServices) {
            responses.add(cachedService.mService);
        }
        return responses;
    }

    /**
     * Get the cache services which are queried from given service type and socket.
     *
     * @param cacheKey the target CacheKey.
     * @return the set of services which matches the given service type.
     */
    @NonNull
    public List<MdnsResponse> getCachedServices(@NonNull CacheKey cacheKey) {
        ensureRunningOnHandlerThread(mHandler);
        if (mMdnsFeatureFlags.mIsExpiredServicesRemovalEnabled) {
            maybeRemoveExpiredServices(cacheKey, mClock.elapsedRealtime());
        }
        return mCachedServices.containsKey(cacheKey)
                ? Collections.unmodifiableList(
                        cachedServicesToResponses(mCachedServices.get(cacheKey)))
                : Collections.emptyList();
    }

    /**
     * Find a matched response for given service name
     *
     * @param responses the responses to be searched.
     * @param serviceName the target service name
     * @return the response which matches the given service name or null if not found.
     */
    public static MdnsResponse findMatchedResponse(@NonNull List<MdnsResponse> responses,
            @NonNull String serviceName) {
        for (MdnsResponse response : responses) {
            if (equalsIgnoreDnsCase(serviceName, response.getServiceInstanceName())) {
                return response;
            }
        }
        return null;
    }

    private static CachedService findMatchedCachedService(
            @NonNull List<CachedService> cachedServices, @NonNull String serviceName) {
        for (CachedService cachedService : cachedServices) {
            if (equalsIgnoreDnsCase(serviceName, cachedService.mService.getServiceInstanceName())) {
                return cachedService;
            }
        }
        return null;
    }

    /**
     * Get the cache service.
     *
     * @param serviceName the target service name.
     * @param cacheKey the target CacheKey.
     * @return the service which matches given conditions.
     */
    @Nullable
    public MdnsResponse getCachedService(@NonNull String serviceName, @NonNull CacheKey cacheKey) {
        ensureRunningOnHandlerThread(mHandler);
        if (mMdnsFeatureFlags.mIsExpiredServicesRemovalEnabled) {
            maybeRemoveExpiredServices(cacheKey, mClock.elapsedRealtime());
        }
        final List<CachedService> cachedServices = mCachedServices.get(cacheKey);
        if (cachedServices == null) {
            return null;
        }
        final CachedService cachedService = findMatchedCachedService(cachedServices, serviceName);
        return cachedService != null ? new MdnsResponse(cachedService.mService) : null;
    }

    static void insertServiceAndSortList(
            List<CachedService> cachedServices, CachedService cachedService, long now) {
        // binarySearch returns "the index of the search key, if it is contained in the list;
        // otherwise, (-(insertion point) - 1)"
        final int searchRes = Collections.binarySearch(cachedServices, cachedService,
                // Sort the list by ttl.
                (o1, o2) -> Long.compare(o1.mService.getMinRemainingTtl(now),
                        o2.mService.getMinRemainingTtl(now)));
        cachedServices.add(searchRes >= 0 ? searchRes : (-searchRes - 1), cachedService);
    }

    /**
     * Add or update a service.
     *
     * @param cacheKey the target CacheKey.
     * @param response the response of the discovered service.
     */
    public void addOrUpdateService(@NonNull CacheKey cacheKey, @NonNull MdnsResponse response) {
        ensureRunningOnHandlerThread(mHandler);
        final List<CachedService> cachedServices = mCachedServices.computeIfAbsent(
                cacheKey, key -> new ArrayList<>());
        // Remove existing service if present.
        final CachedService existing = findMatchedCachedService(cachedServices,
                response.getServiceInstanceName());
        cachedServices.remove(existing);

        final CachedService cachedService = new CachedService(response);
        if (mMdnsFeatureFlags.mIsExpiredServicesRemovalEnabled) {
            final long now = mClock.elapsedRealtime();
            // Insert and sort service
            insertServiceAndSortList(cachedServices, cachedService, now);
            // Update the next expiration check time when a new service is added.
            mNextExpirationTime = getNextExpirationTime(now);
        } else {
            cachedServices.add(cachedService);
        }
    }

    /**
     * Remove a service which matches the given service name, type and socket.
     *
     * @param serviceName the target service name.
     * @param cacheKey the target CacheKey.
     */
    @Nullable
    public MdnsResponse removeService(@NonNull String serviceName, @NonNull CacheKey cacheKey) {
        ensureRunningOnHandlerThread(mHandler);
        final List<CachedService> cachedServices = mCachedServices.get(cacheKey);
        if (cachedServices == null) {
            return null;
        }
        final Iterator<CachedService> iterator = cachedServices.iterator();
        CachedService removedService = null;
        while (iterator.hasNext()) {
            final CachedService cachedService = iterator.next();
            if (equalsIgnoreDnsCase(serviceName, cachedService.mService.getServiceInstanceName())) {
                iterator.remove();
                removedService = cachedService;
                break;
            }
        }

        if (mMdnsFeatureFlags.mIsExpiredServicesRemovalEnabled) {
            // Remove the serviceType if no response.
            if (cachedServices.isEmpty()) {
                mCachedServices.remove(cacheKey);
            }
            // Update the next expiration check time when a service is removed.
            mNextExpirationTime = getNextExpirationTime(mClock.elapsedRealtime());
        }
        return removedService == null ? null : removedService.mService;
    }

    /**
     * Remove services which matches the given type and socket.
     *
     * @param cacheKey the target CacheKey.
     */
    public void removeServices(@NonNull CacheKey cacheKey) {
        ensureRunningOnHandlerThread(mHandler);
        // Remove all services
        if (mCachedServices.remove(cacheKey) == null) {
            return;
        }
        // Update the next expiration check time if services are removed.
        mNextExpirationTime = getNextExpirationTime(mClock.elapsedRealtime());
    }

    /**
     * Register a callback to listen to service expiration.
     *
     * <p> Registering the same callback instance twice is a no-op, since MdnsServiceTypeClient
     * relies on this.
     *
     * @param cacheKey the target CacheKey.
     * @param callback the callback that notify the service is expired.
     */
    public void registerServiceExpiredCallback(@NonNull CacheKey cacheKey,
            @NonNull ServiceExpiredCallback callback) {
        ensureRunningOnHandlerThread(mHandler);
        mCallbacks.put(cacheKey, callback);
    }

    /**
     * Unregister the service expired callback.
     *
     * @param cacheKey the CacheKey that is registered to listen service expiration before.
     */
    public void unregisterServiceExpiredCallback(@NonNull CacheKey cacheKey) {
        ensureRunningOnHandlerThread(mHandler);
        mCallbacks.remove(cacheKey);
    }

    private void notifyServiceExpired(@NonNull CacheKey cacheKey,
            @NonNull MdnsResponse previousResponse, @Nullable MdnsResponse newResponse) {
        final ServiceExpiredCallback callback = mCallbacks.get(cacheKey);
        if (callback == null) {
            // The cached service is no listener.
            return;
        }
        mHandler.post(()-> callback.onServiceRecordExpired(previousResponse, newResponse));
    }

    static List<CachedService> removeExpiredServices(@NonNull List<CachedService> cachedServices,
            long now) {
        final List<CachedService> removedServices = new ArrayList<>();
        final Iterator<CachedService> iterator = cachedServices.iterator();
        while (iterator.hasNext()) {
            final CachedService cachedService = iterator.next();
            // TODO: Check other records (A, AAAA, TXT) ttl time and remove the record if it's
            //  expired. Then send service update notification.
            if (!cachedService.mService.hasServiceRecord()
                    || cachedService.mService.getMinRemainingTtl(now) > 0) {
                // The responses are sorted by the service record ttl time. Break out of loop
                // early if service is not expired or no service record.
                break;
            }
            // Remove the ttl expired service.
            iterator.remove();
            removedServices.add(cachedService);
        }
        return removedServices;
    }

    private long getNextExpirationTime(long now) {
        if (mCachedServices.isEmpty()) {
            return EXPIRATION_NEVER;
        }

        long minRemainingTtl = EXPIRATION_NEVER;
        for (int i = 0; i < mCachedServices.size(); i++) {
            minRemainingTtl = min(minRemainingTtl,
                    // The empty lists are not kept in the map, so there's always at least one
                    // element in the list. Therefore, it's fine to get the first element without a
                    // null check.
                    mCachedServices.valueAt(i).get(0).mService.getMinRemainingTtl(now));
        }
        return minRemainingTtl == EXPIRATION_NEVER ? EXPIRATION_NEVER : now + minRemainingTtl;
    }

    /**
     * Check whether the ttl time is expired on each service and notify to the listeners
     */
    private void maybeRemoveExpiredServices(CacheKey cacheKey, long now) {
        ensureRunningOnHandlerThread(mHandler);
        if (now < mNextExpirationTime) {
            // Skip the check if ttl time is not expired.
            return;
        }

        final List<CachedService> cachedServices = mCachedServices.get(cacheKey);
        if (cachedServices == null) {
            // No such services.
            return;
        }

        final List<CachedService> removedServices = removeExpiredServices(cachedServices, now);
        if (removedServices.isEmpty()) {
            // No expired services.
            return;
        }

        for (CachedService previousService : removedServices) {
            notifyServiceExpired(cacheKey, previousService.mService, null /* newResponse */);
        }

        // Remove the serviceType if no response.
        if (cachedServices.isEmpty()) {
            mCachedServices.remove(cacheKey);
        }

        // Update next expiration time.
        mNextExpirationTime = getNextExpirationTime(now);
    }

    /**
     * Dump ServiceCache state.
     */
    public void dump(PrintWriter pw, String indent) {
        ensureRunningOnHandlerThread(mHandler);
        // IndentingPrintWriter cannot be used on the mDNS stack build. So, manually add an indent.
        for (int i = 0; i < mCachedServices.size(); i++) {
            final CacheKey key = mCachedServices.keyAt(i);
            pw.println(indent + key);
            for (CachedService cachedService : mCachedServices.valueAt(i)) {
                pw.println(indent + "  Response{ " + cachedService.mService
                        + " } Expired=" + cachedService.mServiceExpired);
            }
            pw.println();
        }
    }

    /*** Callbacks for listening service expiration */
    public interface ServiceExpiredCallback {
        /*** Notify the service is expired */
        void onServiceRecordExpired(@NonNull MdnsResponse previousResponse,
                @Nullable MdnsResponse newResponse);
    }

    // TODO: Schedule a job to check ttl expiration for all services and notify to the clients.
}
