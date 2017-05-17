package com.netflix.evcache.pool;

import java.lang.management.ManagementFactory;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.DynamicIntProperty;
import com.netflix.evcache.util.EVCacheConfig;

public class EVCacheScheduledExecutor extends ScheduledThreadPoolExecutor implements EVCacheScheduledExecutorMBean {

    private static final Logger log = LoggerFactory.getLogger(EVCacheScheduledExecutor.class);
    private final DynamicIntProperty maxAsyncPoolSize;
    private final DynamicIntProperty coreAsyncPoolSize;
    private final String name;

    public EVCacheScheduledExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, RejectedExecutionHandler handler, String name) {
        super(corePoolSize, handler);
        this.name = name;

        maxAsyncPoolSize = EVCacheConfig.getInstance().getDynamicIntProperty("EVCacheThreadPool." + name + ".evcache.max.async.poolsize", maximumPoolSize);
        setMaximumPoolSize(maxAsyncPoolSize.get());
        coreAsyncPoolSize = EVCacheConfig.getInstance().getDynamicIntProperty("EVCacheClientPoolManager." + name + ".evcache.core.async.poolsize", corePoolSize);
        setCorePoolSize(coreAsyncPoolSize.get());
        setKeepAliveTime(keepAliveTime, unit);
        final ThreadFactory asyncFactory = new ThreadFactoryBuilder().setDaemon(true).setNameFormat( "EVCacheThreadPool-" + name + "-%d").build();
        setThreadFactory(asyncFactory);
        maxAsyncPoolSize.addCallback(new Runnable() {
            public void run() {
                setMaximumPoolSize(maxAsyncPoolSize.get());
            }
        });
        coreAsyncPoolSize.addCallback(new Runnable() {
            public void run() {
                setCorePoolSize(coreAsyncPoolSize.get());
            }
        });
        
        setupMonitoring(name);
    }

    private void setupMonitoring(String name) {
        try {
            ObjectName mBeanName = ObjectName.getInstance("com.netflix.evcache:Group=ThreadPool,SubGroup="+name);
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            if (mbeanServer.isRegistered(mBeanName)) {
                if (log.isDebugEnabled()) log.debug("MBEAN with name " + mBeanName + " has been registered. Will unregister the previous instance and register a new one.");
                mbeanServer.unregisterMBean(mBeanName);
            }
            mbeanServer.registerMBean(this, mBeanName);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("Exception", e);
        }
    }

    public void shutdown() {
        try {
            ObjectName mBeanName = ObjectName.getInstance("com.netflix.evcache:Group=ThreadPool,SubGroup="+name);
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            mbeanServer.unregisterMBean(mBeanName);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("Exception", e);
        }
        super.shutdown();
    }


}