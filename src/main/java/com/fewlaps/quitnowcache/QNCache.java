package com.fewlaps.quitnowcache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QNCache<T> {

    public static final long KEEPALIVE_FOREVER = 0;

    private final ConcurrentHashMap<String, QNCacheBean<T>> cache;
    private boolean caseSensitiveKeys = true;
    private Integer autoReleaseInSeconds;
    private Long defaultKeepaliveInMillis;
    private DateProvider dateProvider = DateProvider.SYSTEM;

    public QNCache(boolean caseSensitiveKeys, Integer autoReleaseInSeconds, Long defaultKeepaliveInMillis) {
        this.caseSensitiveKeys = caseSensitiveKeys;
        if (autoReleaseInSeconds != null && autoReleaseInSeconds > 0) {
            this.autoReleaseInSeconds = autoReleaseInSeconds;
        }
        if (defaultKeepaliveInMillis != null && defaultKeepaliveInMillis > 0) {
            this.defaultKeepaliveInMillis = defaultKeepaliveInMillis;
        }

        cache = new ConcurrentHashMap<String, QNCacheBean<T>>();

        startAutoReleaseServiceIfNeeded();
    }

    private void startAutoReleaseServiceIfNeeded() {
        if (autoReleaseInSeconds != null) {
            ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

            ses.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    purge();
                }
            }, autoReleaseInSeconds, autoReleaseInSeconds, TimeUnit.SECONDS);
        }
    }

    boolean isCaseSensitiveKeys() {
        return caseSensitiveKeys;
    }

    Integer getAutoReleaseInSeconds() {
        return autoReleaseInSeconds;
    }

    Long getDefaultKeepaliveInMillis() {
        return defaultKeepaliveInMillis;
    }

    private long now() {
        return dateProvider.now();
    }

    protected void setDateProvider(DateProvider dateProvider) {
        this.dateProvider = dateProvider;
    }

    public void set(String key, T value) {
        if (defaultKeepaliveInMillis != null) {
            set(key, value, defaultKeepaliveInMillis);
        } else {
            set(key, value, KEEPALIVE_FOREVER);
        }
    }

    public void set(String key, T value, long keepAliveInMillis) {
        String effectiveKey = getEffectiveKey(key);

        if (keepAliveInMillis >= 0) {
            cache.put(effectiveKey, new QNCacheBean<T>(value, now(), keepAliveInMillis));
        }
    }

    public List<String> listCachedKeysStartingWith(String keyStartingWith) {
        List<String> keys = new ArrayList<String>();
        String effectiveKeyStartingWith = getEffectiveKey(keyStartingWith);

        for (String key : Collections.list(cache.keys())) {
            if (key.startsWith(effectiveKeyStartingWith)) {
                keys.add(key);
            }
        }

        return keys;
    }

    public List<String> listCachedKeysStartingWithIfAlive(String keyStartingWith) {
        List<String> keys = new ArrayList<String>();
        long now = now();
        String effectiveKeyStartingWith = getEffectiveKey(keyStartingWith);

        for (String key : Collections.list(cache.keys())) {
            if (key.startsWith(effectiveKeyStartingWith) && cache.get(key).isAlive(now)) {
                keys.add(key);
            }
        }

        return keys;
    }

    public void set(String key, T value, long keepAliveUnits, TimeUnit timeUnit) {
        set(key, value, timeUnit.toMillis(keepAliveUnits));
    }

    /**
     * Gets an element from the cache.
     */
    public T get(String key) {
        String effectiveKey = getEffectiveKey(key);

        QNCacheBean<T> retrievedValue = cache.get(effectiveKey);
        if (retrievedValue == null || !retrievedValue.isAlive(now())) {
            return null;
        } else {
            return retrievedValue.getValue();
        }
    }

    /**
     * Gets an element from the cache. If the element exists but it's dead,
     * it will be removed of the cache, to free memory
     */
    T getAndRemoveIfDead(String key) {
        String effectiveKey = getEffectiveKey(key);

        QNCacheBean<T> retrievedValue = cache.get(effectiveKey);
        if (retrievedValue == null) {
            return null;
        } else if (retrievedValue.isAlive(now())) {
            return retrievedValue.getValue();
        } else {
            cache.remove(effectiveKey);
            return null;
        }
    }

    public void remove(String key) {
        String effectiveKey = getEffectiveKey(key);
        cache.remove(effectiveKey);
    }

    /**
     * Removes all the elements of the cache, ignoring if they're dead or alive
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Removes the dead elements of the cache, to free memory
     */
    void purge() {
        Iterator<Map.Entry<String, QNCacheBean<T>>> it = cache.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, QNCacheBean<T>> entry = it.next();
            QNCacheBean<T> bean = entry.getValue();
            if (!bean.isAlive(now())) {
                it.remove();
            }
        }
    }

    /**
     * Counts how much alive elements are living in the cache
     */
    public int size() {
        return sizeCountingOnlyAliveElements();
    }

    /**
     * Counts how much alive elements are living in the cache
     */
    int sizeCountingOnlyAliveElements() {
        int size = 0;

        for (QNCacheBean<T> cacheValue : cache.values()) {
            if (cacheValue.isAlive(now())) {
                size++;
            }
        }
        return size;
    }

    /**
     * Counts how much elements are living in the cache, ignoring if they are dead or alive
     */
    int sizeCountingDeadAndAliveElements() {
        return cache.size();
    }

    /**
     * The common isEmpty() method, but only looking for alive elements
     */
    public boolean isEmpty() {
        return sizeCountingOnlyAliveElements() == 0;
    }

    public boolean contains(String key) {
        String effectiveKey = getEffectiveKey(key);
        return get(effectiveKey) != null;
    }

    /**
     * If caseSensitiveKeys is false, it returns a key in lowercase. It will be
     * the key of all stored values, so the cache will be totally caseinsensitive
     */
    private String getEffectiveKey(String key) {
        if (!caseSensitiveKeys) {
            return key.toLowerCase();
        }
        return key;
    }

}
