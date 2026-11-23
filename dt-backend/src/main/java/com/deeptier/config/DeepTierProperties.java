package com.deeptier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deeptier")
public record DeepTierProperties(
        AgentProps agent,
        InternalProps internal,
        MonteCarloProps monteCarlo,
        OptimizerProps optimizer
) {
    public record AgentProps(String runtime) {}
    public record InternalProps(String key) {}
    public record MonteCarloProps(long defaultSeed, int trials) {}
    public record OptimizerProps(int maxLegs, int topK) {}
}
