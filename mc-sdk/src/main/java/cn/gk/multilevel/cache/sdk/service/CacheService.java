package cn.gk.multilevel.cache.sdk.service;

import cn.gk.multilevel.cache.sdk.api.McTemplate;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h3>multilevel-cache-solution</h3>
 * <h4>cn.gk.multilevel.cache.sdk.service</h4>
 * <p>缓存处理</p>
 *
 * @author zora
 * @since 2020.07.15
 */
@Service
@Slf4j
public class CacheService implements McTemplate {
    private volatile static Cache<String, String> localCache;
    private static final int DEFAULT_REDIS_TTL = 3 * 60;
    private static StringRedisTemplate stringRedisTemplate;
    private static final ScheduledThreadPoolExecutor SCHEDULE_EXECUTOR = new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder().setNameFormat("MC-Reporter").build());

    @Autowired
    public void initializeCache(Cache<String, String> cache) {
        localCache = cache;
        log.info("[Multilevel-Cache]----已加载缓存配置{}", cache.getClass());
    }

    @Autowired
    public void initializeCache(StringRedisTemplate autowiredStringRedisTemplate) {
        stringRedisTemplate = autowiredStringRedisTemplate;
        log.info("[Multilevel-Cache]----已加载Redis配置{}", autowiredStringRedisTemplate.getClass());
    }

    @PostConstruct
    private void scheduleReporter() {
        SCHEDULE_EXECUTOR.scheduleWithFixedDelay(() -> {
            log.info("[Multilevel-Cache]----当前本地缓存报告：{}", localCache.stats().toString());
        }, 15, 60, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取caffeine中的缓存数据
     *
     * @param key 缓存key
     * @return json形式的value或null
     */
    private String tryGetFromRam(@NonNull String key) {
        return localCache.getIfPresent(key);
    }

    /**
     * 尝试获取Redis中的缓存数据
     *
     * @param key 缓存key
     * @return json形式的value或null
     */
    private String tryGetFromRedis(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 链式尝试获取数据，先Ram后Redis
     *
     * @param key 缓存key
     * @return json形式的value或null
     */
    private String tryGetValueByChainTrace(String key) {
        String targetValue;
        targetValue = tryGetFromRam(key);
        if (StringUtils.isEmpty(targetValue)) {
            targetValue = tryGetFromRedis(key);
        }
        return targetValue;
    }

    /**
     * 尝试获取缓存中的对象，可能会拿到null
     *
     * @param key   缓存的key
     * @param clazz 反序列化到的类
     * @return 对象或null
     * @throws JSONException json序列化失败
     */
    @Override
    public <V> V tryGetValue(@NonNull String key, Class<V> clazz) throws JSONException {
        return JSON.parseObject(tryGetValueByChainTrace(key), clazz);
    }

    /**
     * 尝试获取缓存中的列表对象，可能会拿到null
     *
     * @param key   缓存的key
     * @param clazz 反序列化到的类
     * @return 对象列表或null
     * @throws JSONException json序列化失败
     */
    @Override
    public <V> List<V> tryGetValueArrays(@NonNull String key, Class<V> clazz) throws JSONException {
        return JSON.parseArray(tryGetValueByChainTrace(key), clazz);
    }

    /**
     * 写入缓存
     *
     * @param key   缓存key
     * @param value 缓存对象
     */
    @Override
    public <T> void putObjectIntoCache(@NonNull String key, T value) {
        putObjectIntoCache(key, value, DEFAULT_REDIS_TTL);
    }

    /**
     * 写入缓存
     *
     * @param key   缓存key
     * @param value 缓存对象
     * @param ttl   过期时间，单位秒
     */
    @Override
    public <T> void putObjectIntoCache(@NonNull String key, T value, long ttl) {
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value), ttl, TimeUnit.SECONDS);
    }

    /**
     * 清理本地缓存空间
     */
    @Override
    public void cleanUpRamCache() {
        localCache.cleanUp();
    }

    /**
     * 清理本地缓存空间
     */
    @Override
    public void cleanCacheByKey(@NonNull String key) {
        stringRedisTemplate.delete(key);
        localCache.invalidate(key);
    }

}