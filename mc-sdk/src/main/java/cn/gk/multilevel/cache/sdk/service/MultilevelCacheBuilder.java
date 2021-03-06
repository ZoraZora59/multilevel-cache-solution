package cn.gk.multilevel.cache.sdk.service;

import cn.gk.multilevel.cache.sdk.model.CacheConfiguration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * <h3>multilevel-cache-solution</h3>
 * <h4>cn.gk.multilevel.cache.sdk.api</h4>
 * <p>多级缓存-缓存生成器</p>
 *
 * @author zora
 * @since 2020.07.15
 */
class MultilevelCacheBuilder {
    /**
     * 通过配置类生成Caffeine缓存
     *
     * @param cacheConfiguration 自定义或默认的缓存配置类
     * @return 本地使用的RamCache
     */
    public static Cache<String, String> buildWithConfiguration(CacheConfiguration cacheConfiguration) {
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();
        if (cacheConfiguration.isSoftValues()) {
            caffeineBuilder.softValues();
        }
        if (cacheConfiguration.isLimitByWeight()) {
            caffeineBuilder.weigher(cacheConfiguration.getWeigher())
                    .maximumWeight(cacheConfiguration.getMemoryUsageSize());
        } else {
            caffeineBuilder.maximumSize(cacheConfiguration.getLocalCacheCountSize());
        }
        return caffeineBuilder.build();
    }
}
