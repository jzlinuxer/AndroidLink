/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.thread;

import static com.android.net.module.util.DeviceConfigUtils.TETHERING_MODULE_NAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ApexEnvironment;
import android.content.Context;
import android.net.thread.ThreadConfiguration;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.ConnectivityResources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Store persistent data for Thread network settings. These are key (string) / value pairs that are
 * stored in ThreadPersistentSetting.xml file. The values allowed are those that can be serialized
 * via {@link PersistableBundle}.
 */
public class ThreadPersistentSettings {
    private static final String TAG = "ThreadPersistentSettings";
    private static final SharedLog LOG = ThreadNetworkLogger.forSubComponent(TAG);

    /** File name used for storing settings. */
    private static final String FILE_NAME = "ThreadPersistentSettings.xml";

    /** Current config store data version. This MUST be incremented for any incompatible changes. */
    private static final int CURRENT_SETTINGS_STORE_DATA_VERSION = 1;

    /**
     * Stores the version of the data. This can be used to handle migration of data if some
     * non-backward compatible change introduced.
     */
    private static final String KEY_VERSION = "version";

    /**
     * Saves the boolean flag for Thread being enabled. The value defaults to resource overlay value
     * {@code R.bool.config_thread_default_enabled}.
     */
    public static final Key<Boolean> KEY_THREAD_ENABLED = new Key<>("thread_enabled");

    /**
     * Saves the boolean flag for border router being enabled. The value defaults to resource
     * overlay value {@code R.bool.config_thread_border_router_default_enabled}.
     */
    private static final Key<Boolean> KEY_CONFIG_BORDER_ROUTER_ENABLED =
            new Key<>("config_border_router_enabled");

    /** Stores the Thread NAT64 feature toggle state, true for enabled and false for disabled. */
    private static final Key<Boolean> KEY_CONFIG_NAT64_ENABLED = new Key<>("config_nat64_enabled");

    /**
     * Stores the Thread DHCPv6-PD feature toggle state, true for enabled and false for disabled.
     */
    private static final Key<Boolean> KEY_CONFIG_DHCP6_PD_ENABLED =
            new Key<>("config_dhcp6_pd_enabled");

    /**
     * Indicates that Thread was enabled (i.e. via the setEnabled() API) when the airplane mode is
     * turned on in settings. When this value is {@code true}, the current airplane mode state will
     * be ignored when evaluating the Thread enabled state.
     */
    public static final Key<Boolean> KEY_THREAD_ENABLED_IN_AIRPLANE_MODE =
            new Key<>("thread_enabled_in_airplane_mode");

    /** Stores the Thread country code, null if no country code is stored. */
    public static final Key<String> KEY_COUNTRY_CODE = new Key<>("thread_country_code");

    @GuardedBy("mLock")
    private final AtomicFile mAtomicFile;

    private final Object mLock = new Object();

    private final Map<String, Object> mDefaultValues = new HashMap<>();

    @GuardedBy("mLock")
    private final PersistableBundle mSettings = new PersistableBundle();

    private final ConnectivityResources mResources;

    public static ThreadPersistentSettings newInstance(Context context) {
        return new ThreadPersistentSettings(
                new AtomicFile(new File(getOrCreateThreadNetworkDir(), FILE_NAME)),
                new ConnectivityResources(context));
    }

    @VisibleForTesting
    ThreadPersistentSettings(AtomicFile atomicFile, ConnectivityResources resources) {
        mAtomicFile = atomicFile;
        mResources = resources;

        mDefaultValues.put(
                KEY_THREAD_ENABLED.key,
                mResources.get().getBoolean(R.bool.config_thread_default_enabled));
        mDefaultValues.put(
                KEY_CONFIG_BORDER_ROUTER_ENABLED.key,
                mResources.get().getBoolean(R.bool.config_thread_border_router_default_enabled));
        mDefaultValues.put(KEY_CONFIG_NAT64_ENABLED.key, false);
        mDefaultValues.put(KEY_CONFIG_DHCP6_PD_ENABLED.key, false);
        mDefaultValues.put(KEY_THREAD_ENABLED_IN_AIRPLANE_MODE.key, false);
        mDefaultValues.put(KEY_COUNTRY_CODE.key, null);
    }

    /** Initialize the settings by reading from the settings file. */
    public void initialize() {
        readFromStoreFile();
    }

    private void putObject(String key, @Nullable Object value) {
        synchronized (mLock) {
            if (value == null) {
                mSettings.putString(key, null);
            } else if (value instanceof Boolean) {
                mSettings.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                mSettings.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                mSettings.putLong(key, (Long) value);
            } else if (value instanceof Double) {
                mSettings.putDouble(key, (Double) value);
            } else if (value instanceof String) {
                mSettings.putString(key, (String) value);
            } else {
                throw new IllegalArgumentException("Unsupported type " + value.getClass());
            }
        }
    }

    private <T> T getObject(String key, T defaultValue) {
        Object value;
        synchronized (mLock) {
            if (defaultValue == null) {
                value = mSettings.getString(key, null);
            } else if (defaultValue instanceof Boolean) {
                value = mSettings.getBoolean(key, (Boolean) defaultValue);
            } else if (defaultValue instanceof Integer) {
                value = mSettings.getInt(key, (Integer) defaultValue);
            } else if (defaultValue instanceof Long) {
                value = mSettings.getLong(key, (Long) defaultValue);
            } else if (defaultValue instanceof Double) {
                value = mSettings.getDouble(key, (Double) defaultValue);
            } else if (defaultValue instanceof String) {
                value = mSettings.getString(key, (String) defaultValue);
            } else {
                throw new IllegalArgumentException("Unsupported type " + defaultValue.getClass());
            }
        }
        return (T) value;
    }

    /** Stores a value to the stored settings. */
    public <T> void put(Key<T> key, @Nullable T value) {
        putObject(key.key, value);
        writeToStoreFile();
    }

    /** Retrieves a value from the stored settings. */
    @Nullable
    public <T> T get(Key<T> key) {
        T defaultValue = (T) mDefaultValues.get(key.key);
        return getObject(key.key, defaultValue);
    }

    /**
     * Store a {@link ThreadConfiguration} to the persistent settings.
     *
     * @param configuration {@link ThreadConfiguration} to be stored.
     * @return {@code true} if the configuration was changed, {@code false} otherwise.
     */
    public boolean putConfiguration(@NonNull ThreadConfiguration configuration) {
        if (getConfiguration().equals(configuration)) {
            return false;
        }
        put(KEY_CONFIG_BORDER_ROUTER_ENABLED, configuration.isBorderRouterEnabled());
        put(KEY_CONFIG_NAT64_ENABLED, configuration.isNat64Enabled());
        put(KEY_CONFIG_DHCP6_PD_ENABLED, configuration.isDhcpv6PdEnabled());
        writeToStoreFile();
        return true;
    }

    /** Retrieve the {@link ThreadConfiguration} from the persistent settings. */
    public ThreadConfiguration getConfiguration() {
        return new ThreadConfiguration.Builder()
                .setBorderRouterEnabled(get(KEY_CONFIG_BORDER_ROUTER_ENABLED))
                .setNat64Enabled(get(KEY_CONFIG_NAT64_ENABLED))
                .setDhcpv6PdEnabled(get(KEY_CONFIG_DHCP6_PD_ENABLED))
                .build();
    }

    /**
     * Base class to store string key and its default value.
     *
     * @param <T> Type of the value.
     */
    public static final class Key<T> {
        @VisibleForTesting final String key;

        private Key(String key) {
            this.key = key;
        }
    }

    private void writeToStoreFile() {
        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final PersistableBundle bundleToWrite;
            synchronized (mLock) {
                bundleToWrite = new PersistableBundle(mSettings);
            }
            bundleToWrite.putInt(KEY_VERSION, CURRENT_SETTINGS_STORE_DATA_VERSION);
            bundleToWrite.writeToStream(outputStream);
            synchronized (mLock) {
                writeToAtomicFile(mAtomicFile, outputStream.toByteArray());
            }
        } catch (IOException e) {
            LOG.wtf("Write to store file failed", e);
        }
    }

    private void readFromStoreFile() {
        try {
            final byte[] readData;
            synchronized (mLock) {
                LOG.i("Reading from store file: " + mAtomicFile.getBaseFile());
                readData = readFromAtomicFile(mAtomicFile);
            }
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(readData);
            final PersistableBundle bundleRead = PersistableBundle.readFromStream(inputStream);
            // Version unused for now. May be needed in the future for handling migrations.
            bundleRead.remove(KEY_VERSION);
            synchronized (mLock) {
                mSettings.putAll(bundleRead);
            }
        } catch (FileNotFoundException e) {
            LOG.w("No store file to read " + e.getMessage());
        } catch (IOException e) {
            LOG.e("Read from store file failed", e);
        }
    }

    /**
     * Read raw data from the atomic file. Note: This is a copy of {@link AtomicFile#readFully()}
     * modified to use the passed in {@link InputStream} which was returned using {@link
     * AtomicFile#openRead()}.
     */
    private static byte[] readFromAtomicFile(AtomicFile file) throws IOException {
        FileInputStream stream = null;
        try {
            stream = file.openRead();
            int pos = 0;
            int avail = stream.available();
            byte[] data = new byte[avail];
            while (true) {
                int amt = stream.read(data, pos, data.length - pos);
                if (amt <= 0) {
                    return data;
                }
                pos += amt;
                avail = stream.available();
                if (avail > data.length - pos) {
                    byte[] newData = new byte[pos + avail];
                    System.arraycopy(data, 0, newData, 0, pos);
                    data = newData;
                }
            }
        } finally {
            if (stream != null) stream.close();
        }
    }

    /** Write the raw data to the atomic file. */
    private static void writeToAtomicFile(AtomicFile file, byte[] data) throws IOException {
        // Write the data to the atomic file.
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(data);
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) {
                file.failWrite(out);
            }
            throw e;
        }
    }

    /** Get device protected storage dir for the tethering apex. */
    private static File getOrCreateThreadNetworkDir() {
        final File threadnetworkDir;
        final File apexDataDir =
                ApexEnvironment.getApexEnvironment(TETHERING_MODULE_NAME)
                        .getDeviceProtectedDataDir();
        threadnetworkDir = new File(apexDataDir, "thread");

        if (threadnetworkDir.exists() || threadnetworkDir.mkdirs()) {
            return threadnetworkDir;
        }
        throw new IllegalStateException(
                "Cannot write into thread network data directory: " + threadnetworkDir);
    }
}
