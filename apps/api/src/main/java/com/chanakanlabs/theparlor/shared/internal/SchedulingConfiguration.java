package com.chanakanlabs.theparlor.shared.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs. Set theparlor.scheduling.enabled=false to run none of them. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = "theparlor.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
class SchedulingConfiguration {}
