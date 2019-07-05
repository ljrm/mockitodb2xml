package com.zdmoney.RetryPolicy;


import com.zdmoney.DistributedLocksFactory.DistributedLock;
import com.zdmoney.action.FailureAction;

/**
 * Created by user on 2018/7/6.
 */
public interface RetryPolicy {
    void retry();
    boolean isFailed();
    void afterFailure();
    void setFailureAction(FailureAction action);
    void setDistributionLock(DistributedLock lock);
    DistributedLock getDistributionLock();
}
