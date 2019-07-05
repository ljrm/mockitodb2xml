package com.zdmoney;

import com.zdmoney.RetryPolicy.LimitTimesWaitIntervalPolicy;
import com.zdmoney.RetryPolicy.RetryPolicy;
import com.zdmoney.action.DefaultSilentAction;
import com.zdmoney.action.FailureAction;
import com.zdmoney.exception.UnprocessableException;
import com.zdmoney.exception.ZKException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CreateBuilder;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by user on 2018/7/6.
 */
public class DistributedLocksFactory {
    /**logger**/
    private static final Logger logger = LoggerFactory.getLogger(DistributedLocksFactory.class);

    private RedisTemplate redisTemplate;

    private CuratorFramework curatorFramework;

    private int state = -1;

    public DistributedLocksFactory(RedisTemplate redisTemplate, CuratorFramework curatorFramework) {
        this.redisTemplate = redisTemplate;
        this.curatorFramework = curatorFramework;
    }

    public void prepare(){
        if(state == -1) {
            state = 0;
            try {
                Stat stat = curatorFramework.checkExists().forPath(DistributedLock.BASE_PATH);
                if(stat == null)
                    curatorFramework.create().creatingParentsIfNeeded().forPath(DistributedLock.BASE_PATH);
            } catch (Exception e) {
                e.printStackTrace();
            }
            state = 1;
        }
    }

    public RedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    public CuratorFramework getCuratorFramework() {
        return curatorFramework;
    }

    public DistributedLock createLock(String lockKey, FailureAction action, RetryPolicy retryPolicy){
        return new DistributedLock(lockKey,retryPolicy,action,this);
    }

    public DistributedLock createLock(String lockKey){
        return new DistributedLock(lockKey,this);
    }

    public static class DistributedLock {
        /**logger**/
        private static final Logger lockLogger = LoggerFactory.getLogger(DistributedLock.class);

        public final static String BASE_PATH = "/com/zdmoney/dls";

        private static final int UNLOCKED = -1;

        private static final int LOCKING = 0;

        private static final int LOCKED = 1;

        private static final String lockValue = "1";

        private static final String ZK_LOCK_NAME = "reqForLock";

        private final FailureAction action;

        private final String lockKey;

        private final RetryPolicy retryPolicy;

        private DistributedLocksFactory factory;

        private volatile int state = UNLOCKED;

        private String ensuredKeyPath;//formatted valid path for lockKey

        private String watcherPath;//ephemeral watcher path

        private String watcherParentPath;//ephemeral watchers' parent path

        private String permanentLockedPath;//the first lock path,clients try to create

        private String firstLockPath;

        private String realPath;

        private final Object semaphore = new Object();

        private CountDownLatch latch = new CountDownLatch(1);

        private DistributedLock(String lockKey, RetryPolicy retryPolicy, FailureAction action, DistributedLocksFactory factory) {
            this.action = action;
            this.lockKey = lockKey;
            this.retryPolicy = retryPolicy;
            this.factory = factory;
            retryPolicy.setDistributionLock(this);
            retryPolicy.setFailureAction(action);
            setPaths();
        }

        private DistributedLock(String lockKey, DistributedLocksFactory factory) {
            this(lockKey,new LimitTimesWaitIntervalPolicy(),new DefaultSilentAction(),factory);
        }

        private void setPaths(){
            ensuredKeyPath = BASE_PATH + "/" + lockKey;
            watcherParentPath = ensuredKeyPath + "/watchers";
            watcherPath = watcherParentPath +"/"+ ZK_LOCK_NAME;
            permanentLockedPath = ensuredKeyPath + "/lock";
            firstLockPath = permanentLockedPath + "/locked";
        }

        private void createBasePathIfNeeded(){
           /* try {
                Stat stat = factory.getCuratorFramework().checkExists().forPath(BASE_PATH);
                if(stat == null)
                    factory.getCuratorFramework().create().creatingParentsIfNeeded().forPath(BASE_PATH);
            } catch (Exception e) {
                suppressNodeExistsExeception(e,BASE_PATH);
            }*/
            createOrCheckPath(BASE_PATH,true);
        }

        private void ensureKeyPath(){
            /*try {
                Stat stat = factory.getCuratorFramework().checkExists().forPath(ensuredKeyPath);
                if(stat == null)
                    factory.getCuratorFramework().create().forPath(ensuredKeyPath);
            } catch (Exception e) {
                suppressNodeExistsExeception(e,ensuredKeyPath);
            }*/
            createOrCheckPath(ensuredKeyPath,false);
        }

        private boolean createPermanentLockedPath(){
            /*boolean flag = false;
            try {
                Stat stat = factory.getCuratorFramework().checkExists().forPath(permanentLockedPath);
                if(stat == null) {
                    factory.getCuratorFramework().create().forPath(permanentLockedPath);
                    flag = true;
                }
            } catch (Exception e) {
                suppressNodeExistsExeception(e,permanentLockedPath);
            }
            return flag;*/
            return createOrCheckPath(permanentLockedPath,false);
        }

        private void ensureWatcherParentPath(){
            createOrCheckPath(watcherParentPath,false);
        }

        private boolean createOrCheckPath(String path,boolean creatingParentsIfNeeded){
            boolean flag = false;
            try {
                Stat stat = factory.getCuratorFramework().checkExists().forPath(path);
                if(stat == null) {
                    CreateBuilder createBuilder = factory.getCuratorFramework().create();
                    if(creatingParentsIfNeeded)
                        createBuilder.creatingParentsIfNeeded();
                    createBuilder.forPath(path);
                    flag = true;
                }
            } catch (Exception e) {
                suppressNodeExistsExeception(e,path);
            }
            return flag;
        }

        private void createFirstLockPath(){
            try {
                factory.getCuratorFramework().create().withMode(CreateMode.EPHEMERAL).forPath(firstLockPath);
                realPath = firstLockPath;
            } catch (Exception e) {
                lockLogger.error(e.getMessage(),e);
                throw new ZKException("create first lock path failed or check ["+firstLockPath+"] exists failed");
            }
        }

        public void createEphemeralPath(){
            ensureWatcherParentPath();
            try {
                realPath = factory.getCuratorFramework().create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(watcherPath);
            } catch (Exception e) {
                lockLogger.error(e.getMessage(),e);
               throw new ZKException("create watcher path failed:"+watcherPath);
            }
        }

        private void suppressNodeExistsExeception(Exception e,String path){
            if(e instanceof KeeperException.NodeExistsException){
                lockLogger.warn("node [{}] already exists,do not need to create it again",path);
            }else{
                lockLogger.error("create path failed:{}",path);
                throw new RuntimeException(e);
            }
        }

        private String findFormerLockPath(){
            List<String> children = null;
            try {
                children = factory.getCuratorFramework().getChildren().forPath(watcherParentPath);
            } catch (Exception e) {
                throw new ZKException("find children failed:" + watcherParentPath);
            }
            Map<Integer,String> map = new HashMap();
            Integer selfKey = Integer.valueOf(Integer.parseInt(realPath.substring(watcherPath.length())));
            for(String child : children){
                map.put(Integer.valueOf(Integer.parseInt(child.substring(ZK_LOCK_NAME.length()))),child);
            }
            List<Integer> keyList = new ArrayList<Integer>(map.keySet());
            if(keyList.get(0).equals(selfKey)) return firstLockPath;//if yourself is the minimum, 'firstLockPath' should be  watching
            Collections.sort(keyList);
            Integer pre = Integer.valueOf(0);
            String formerPath = null;
            for(Integer n : keyList){
                if(n.equals(selfKey)){
                    formerPath = map.get(pre);//find the path according to index right before you
                    break;
                }
                pre = n;
            }
            return watcherParentPath + "/" + formerPath;
        }

        public void waitOnLatch(){
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new UnprocessableException("wait on semaphore failed");
            }
        }

        public void notifyWaiterOnLatch(){
            latch.countDown();
        }

        public boolean setRedisLockIfAbsent(){
           return factory.getRedisTemplate().opsForValue().setIfAbsent(lockKey, lockValue);
        }

        private void addWatcher() {
            final String pathToWatch = findFormerLockPath();
            try {
                Stat stat = factory.getCuratorFramework().getZookeeperClient().getZooKeeper().exists(pathToWatch, new Watcher() {
                    public void process(WatchedEvent event) {
                        if (event.getType().equals(Event.EventType.NodeDeleted)) {
                            lockLogger.info("node deleted:"+pathToWatch);
                            notifyWaiterOnLatch();
                        } else {
                            try {
                                factory.getCuratorFramework().getZookeeperClient().getZooKeeper().exists(pathToWatch, this);
                            } catch (Exception e) {
                                lockLogger.error(e.getMessage(),e);
                            }
                        }
                    }
                });
                if(stat != null){
                    lockLogger.info("watcher path is {} watching on path {}",realPath, pathToWatch);
                    waitOnLatch();
                }
            } catch (Exception e) {
                lockLogger.error(e.getMessage(),e);
                throw new ZKException("add watcher to former path failed:" + pathToWatch);
            }
            try{
                if(!setRedisLockIfAbsent()){//back to thread,if the redis lock remains,invoke retry policy
                    retryPolicy.retry();
                }
            }catch (Exception e){
                lockLogger.error(e.getMessage(),e);
            }
            state = LOCKED;
            lockLogger.info("lock accquired ,the watcher path is:{}",realPath);
        }

        private void removeLockPath(){
            try {
                /*if(isFirstLock)
                    factory.getCuratorFramework().delete().guaranteed().forPath(firstLockPath);//if you are the first lock,delete 'firstLockPath'
                else
                    factory.getCuratorFramework().delete().guaranteed().forPath(realPath);//delete the ephemeral path*/
                factory.getCuratorFramework().delete().guaranteed().forPath(realPath);//delete the lock path
            } catch (Exception e) {
                lockLogger.error(e.getMessage(),e);
            }
        }

        private void clearRedisLock(){
            factory.getRedisTemplate().delete(lockKey);
        }

        public void lock() throws Exception{
            createBasePathIfNeeded();//create base path
            if(state == UNLOCKED){
                synchronized (this){
                    if(state == UNLOCKED) {
                        ensureKeyPath();//create key lock path
                        state = LOCKING;
                        boolean isFirstLock = false;
                        try{
                            isFirstLock = createPermanentLockedPath();//create 'locked' path to clam the first lock has been acquired
                            if(isFirstLock){
                                createFirstLockPath();
                            }
                        }catch (ZKException e){
                            state = UNLOCKED;
                            lockLogger.error(e.getMessage(),e);
                            throw e;
                        }
                        if(isFirstLock){
                            try {
                                factory.getRedisTemplate().opsForValue().set(lockKey, lockValue);
                                state = LOCKED;
                                lockLogger.info("first lock path is:{}",firstLockPath);
                            }catch (Exception e){
                                state = UNLOCKED;
                                lockLogger.error(e.getMessage(),e);
                                removeLockPath();
                                throw new UnprocessableException("Error occurs when connect to redis");
                            }
                            return;
                        }
                        try{
                            createEphemeralPath();
                            addWatcher();
                        }catch (Exception e){
                            lockLogger.error(e.getMessage(),e);
                            state = UNLOCKED;
                            removeLockPath();
                        }
                    }
                }
            }else{
                throw new RuntimeException("Illegal state,can not lock again before unlocked");
            }
        }

        public void unlock(){
            if(state == LOCKED){
                clearRedisLock();
                removeLockPath();
                lockLogger.info("unlock,the watcher path is:{}",realPath);
            }else{
                throw new RuntimeException("Illegal state,can not unlock before locked");
            }
        }
    }

}
