package com.zdmoney;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by user on 2018/7/11.
 */
public class DistributedLockTester {

    public static void main(String[] args){
        /*String n = "reqForLock000001";
        System.out.println(n.substring("reqForLock".length()));
        System.out.println(Integer.parseInt(n.substring("reqForLock".length())));*/
        JedisPoolConfig  jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(200);
        jedisPoolConfig.setTestOnBorrow(true);
        String spring_redis_cluster_nodes="172.17.34.107:7000,172.17.34.107:7001,172.17.34.108:7002,172.17.34.108:7003,172.16.34.109:7004,172.16.34.109:7005,172.17.34.107:7000,172.17.34.107:7001,172.17.34.108:7002,172.17.34.108:7003,172.16.34.109:7004,172.16.34.109:7005,172.17.34.107:7000,172.17.34.107:7001,172.17.34.108:7002,172.17.34.108:7003,172.16.34.109:7004,172.16.34.109:7005";
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        Set<String> hostAndPortSet = StringUtils.commaDelimitedListToSet(spring_redis_cluster_nodes);
        for(String hostAndPort:hostAndPortSet){
            String[] node = StringUtils.split(hostAndPort, ":");
            redisClusterConfiguration.clusterNode(node[0], Integer.valueOf(node[1]).intValue());
        }
        redisClusterConfiguration.setMaxRedirects(Integer.valueOf(3));
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration,jedisPoolConfig);
        jedisConnectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(jedisConnectionFactory);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient("172.17.35.11:2181,172.17.35.12:2181,172.17.35.31:2181", 60000, 20000,
                new ExponentialBackoffRetry(10000, 3));
        curatorFramework.start();
        try {
            curatorFramework.delete().deletingChildrenIfNeeded().forPath("/com/zdmoney/dls");
        } catch (Exception e) {
            e.printStackTrace();
        }
        final DistributedLocksFactory distributedLocksFactory = new DistributedLocksFactory(redisTemplate,curatorFramework);
        /*DistributedLocksFactory.DistributedLock distributedLock = distributedLocksFactory.createLock("product");
        try {
            distributedLock.lock();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            distributedLock.unlock();
        }*/
        ExecutorService executorService = Executors.newCachedThreadPool();

        for(int i = 0 ;i < 100;i++){
            executorService.execute(new Runnable() {
                public void run() {
                    DistributedLocksFactory.DistributedLock distributedLock = distributedLocksFactory.createLock("product");
                    try {
                        distributedLock.lock();
                        TimeUnit.SECONDS.sleep(5);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }finally {
                        distributedLock.unlock();
                    }
                }
            });
        }
        System.out.println("curatorFramework state:"+curatorFramework.getState());
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
