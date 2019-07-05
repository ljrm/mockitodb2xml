package com.zdmoney.RetryPolicy;

import com.zdmoney.DistributedLocksFactory.DistributedLock;
import com.zdmoney.action.FailureAction;

import java.util.concurrent.locks.LockSupport;

/**
 * Created by user on 2018/7/6.
 */
public class LimitTimesWaitIntervalPolicy implements RetryPolicy{

    public static final int DEFAULT_TIMES = 3;

    public static final int DEFAULT_INTERVAL = 2*60;

    private DistributedLock lock;

    private int countLimit = DEFAULT_TIMES;

    private int interval = DEFAULT_INTERVAL;

    private long parkNanos = interval * 1000000000l;

    private int count = 0;

    private FailureAction failureAction;

    public LimitTimesWaitIntervalPolicy(int countLimit, int interval) {
        this.countLimit = countLimit;
        this.interval = interval;
        this.parkNanos = interval * 1000000000l;
    }

    public LimitTimesWaitIntervalPolicy(){

    }

    public void setDistributionLock(DistributedLock lock) {
        this.lock = lock;
    }

    public DistributedLock getDistributionLock() {
        return lock;
    }

    public void retry() {
        if(!isFailed()){
            LockSupport.parkNanos(parkNanos);
           if(!getDistributionLock().setRedisLockIfAbsent()){
               count++;
               retry();
           }
        }else{
            afterFailure();
        }
    }

    public void afterFailure() {
        failureAction.afterFailure();
    }

    public boolean isFailed() {
        if(count > countLimit) return true;
        return false;
    }

    public void setFailureAction(FailureAction action) {
        this.failureAction = action;
    }
}