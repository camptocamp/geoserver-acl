/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.acl.authorization.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;

import lombok.NonNull;

import org.geoserver.acl.domain.event.AdminRuleEvent;
import org.geoserver.acl.domain.event.RuleEvent;
import org.geoserver.acl.model.authorization.AccessInfo;
import org.geoserver.acl.model.authorization.AccessRequest;
import org.geoserver.acl.model.authorization.AuthorizationService;
import org.geoserver.acl.model.rules.Rule;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CachingAuthorizationService implements AuthorizationService {

    private final AuthorizationService delegate;

    private LoadingCache<AccessRequest, AccessInfo> ruleAccessCache;
    private LoadingCache<AccessRequest, AccessInfo> adminRuleAccessCache;

    CachingAuthorizationService(
            @NonNull AuthorizationService delegate, @NonNull CaffeineSpec spec) {
        this(delegate, spec, spec);
    }

    public CachingAuthorizationService(
            @NonNull AuthorizationService delegate,
            @NonNull CaffeineSpec rulesSpec,
            @NonNull CaffeineSpec adminRulesSpec) {

        this.delegate = delegate;
        ruleAccessCache = Caffeine.from(rulesSpec).build(this.delegate::getAccessInfo);
        adminRuleAccessCache =
                Caffeine.from(adminRulesSpec).build(this.delegate::getAdminAuthorization);
    }

    @Override
    public AccessInfo getAccessInfo(AccessRequest request) {
        return ruleAccessCache.get(request);
    }

    @Override
    public AccessInfo getAdminAuthorization(AccessRequest request) {
        return adminRuleAccessCache.get(request);
    }

    @Override
    public List<Rule> getMatchingRules(AccessRequest request) {
        return delegate.getMatchingRules(request);
    }

    @Async
    @EventListener(RuleEvent.class)
    public void onRuleEvent(RuleEvent event) {
        switch (event.getEventType()) {
            case DELETED:
            case UPDATED:
                evictRuleAccessCache(event.getRuleIds());
                break;
            case CREATED:
            default:
                break;
        }
    }

    @Async
    @EventListener(AdminRuleEvent.class)
    public void onAdminRuleEvent(AdminRuleEvent event) {
        switch (event.getEventType()) {
            case DELETED:
            case UPDATED:
                evictAdminAccessCache(event.getRuleIds());
                break;
            case CREATED:
            default:
                break;
        }
    }

    private void evictRuleAccessCache(Set<String> affectedRuleIds) {
        List<AccessRequest> matchingRequests =
                ruleAccessCache.asMap().entrySet().stream()
                        .parallel()
                        .filter(
                                e ->
                                        e.getValue().getMatchingRules().stream()
                                                .anyMatch(affectedRuleIds::contains))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        ruleAccessCache.invalidateAll(matchingRequests);
    }

    private void evictAdminAccessCache(Set<String> affectedRuleIds) {
        Predicate<? super Entry<AccessRequest, AccessInfo>> adminRulePredicate =
                e ->
                        e.getValue().getMatchingAdminRule() != null
                                && affectedRuleIds.contains(e.getValue().getMatchingAdminRule());

        List<AccessRequest> matchingRequests;

        matchingRequests =
                adminRuleAccessCache.asMap().entrySet().stream()
                        .parallel()
                        .filter(adminRulePredicate)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        adminRuleAccessCache.invalidateAll(matchingRequests);

        matchingRequests =
                ruleAccessCache.asMap().entrySet().stream()
                        .parallel()
                        .filter(adminRulePredicate)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        ruleAccessCache.invalidateAll(matchingRequests);
    }

    public static CachingAuthorizationService newShortLivedInstanceForClient(
            AuthorizationService delegate) {
        String clientSettings = "";
        return fromSpec(delegate, clientSettings);
    }

    public static CachingAuthorizationService newLongLivedInstanceForServer(
            AuthorizationService delegate) {
        String serverSettings = "";
        return fromSpec(delegate, serverSettings);
    }

    private static CachingAuthorizationService fromSpec(
            AuthorizationService delegate, String serverSettings) {
        CaffeineSpec spec = CaffeineSpec.parse(serverSettings);
        return new CachingAuthorizationService(delegate, spec);
    }
}
