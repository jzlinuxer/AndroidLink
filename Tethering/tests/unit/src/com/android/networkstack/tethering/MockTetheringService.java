/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.networkstack.tethering;

import static android.Manifest.permission.WRITE_SETTINGS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

public class MockTetheringService extends TetheringService {
    private final Tethering mTethering = mock(Tethering.class);
    private final ArrayMap<String, Integer> mMockedPermissions = new ArrayMap<>();
    private final ArrayMap<String, Integer> mMockedPackageUids = new ArrayMap<>();
    private final Set<String> mMockedDeviceOwnerPackages = new ArraySet<>();
    private final Set<String> mMockedCarrierPrivilegedPackages = new ArraySet<>();
    private int mMockCallingUid;

    @Override
    public IBinder onBind(Intent intent) {
        return new MockTetheringConnector(super.onBind(intent));
    }

    @Override
    public Tethering makeTethering(TetheringDependencies deps) {
        return mTethering;
    }

    @Override
    boolean checkAndNoteWriteSettingsOperation(@NonNull Context context, int uid,
            @NonNull String callingPackage, @Nullable String callingAttributionTag,
            boolean throwException) {
        // Test this does not verify the calling package / UID, as calling package could be shell
        // and not match the UID.
        return context.checkCallingOrSelfPermission(WRITE_SETTINGS) == PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(String permission) {
        final Integer mocked = mMockedPermissions.getOrDefault(permission, null);
        if (mocked != null) {
            return mocked;
        }
        return super.checkCallingOrSelfPermission(permission);
    }

    @Override
    boolean checkPackageNameMatchesUid(@NonNull Context context, int uid,
            @NonNull String callingPackage) {
        return mMockedPackageUids.getOrDefault(callingPackage, 0) == uid;
    }

    @Override
    int getBinderCallingUid() {
        return mMockCallingUid;
    }

    @Override
    boolean isDeviceOwner(final int uid, final String callerPkg) {
        return mMockedDeviceOwnerPackages.contains(callerPkg);
    }

    @Override
    boolean isCarrierPrivileged(final String callerPkg) {
        return mMockedCarrierPrivilegedPackages.contains(callerPkg);
    }

    public Tethering getTethering() {
        return mTethering;
    }

    public class MockTetheringConnector extends Binder {
        final IBinder mBase;
        MockTetheringConnector(IBinder base) {
            mBase = base;
        }

        public IBinder getIBinder() {
            return mBase;
        }

        public MockTetheringService getService() {
            return MockTetheringService.this;
        }

        /**
         * Mock a permission
         * @param permission Permission to mock
         * @param granted One of PackageManager.PERMISSION_*, or null to reset to default behavior
         */
        public void setPermission(String permission, Integer granted) {
            if (granted == null) {
                mMockedPermissions.remove(permission);
            } else {
                mMockedPermissions.put(permission, granted);
            }
        }

        /**
         * Mock a package name matching a uid.
         */
        public void setPackageNameUid(String packageName, int uid) {
            mMockedPackageUids.put(packageName, uid);
        }

        /**
         * Mock a package name matching a uid.
         */
        public void setCallingUid(int uid) {
            mMockCallingUid = uid;
        }

        /**
         * Add a mocked carrier privileges package
         */
        public void addDeviceOwnerPackage(final String packageName) {
            mMockedDeviceOwnerPackages.add(packageName);
        }

        /**
         * Remove a mocked carrier privileges package
         */
        public void removeDeviceOwnerPackage(final String packageName) {
            mMockedDeviceOwnerPackages.remove(packageName);
        }

        /**
         * Add a mocked carrier privileges package
         */
        public void addCarrierPrivilegedPackage(final String packageName) {
            mMockedCarrierPrivilegedPackages.add(packageName);
        }

        /**
         * Remove a mocked carrier privileges package
         */
        public void removeCarrierPrivilegedPackage(final String packageName) {
            mMockedCarrierPrivilegedPackages.remove(packageName);
        }
    }
}
