/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoFence 3.6 under LGPL 2.0 license
 */

package org.geoserver.acl.model.rules;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Value
@With
@Builder(toBuilder = true, builderClassName = "Builder")
public class Rule {

    private static final RuleIdentifier EMPTY_IDENTIFIER = RuleIdentifier.builder().build();

    private String id;

    /**
     * External Id. An ID used in an external systems. This field should simplify Authorization
     * integration in complex systems.
     */
    private String extId;

    private String name;
    private String description;

    private long priority;

    @NonNull @Default private RuleIdentifier identifier = EMPTY_IDENTIFIER;

    private RuleLimits ruleLimits;

    public IPAddressRange ipAddressRange() {
        return getIdentifier().getAddressRange();
    }

    public Rule withInstanceName(String instanceName) {
        return withIdentifier(identifier.withInstanceName(instanceName));
    }

    public Rule withUsername(String username) {
        return withIdentifier(identifier.withUsername(username));
    }

    public Rule withRolename(String rolename) {
        return withIdentifier(identifier.withRolename(rolename));
    }

    public Rule withService(String service) {
        return withIdentifier(identifier.withService(service));
    }

    public Rule withAddressRange(IPAddressRange ar) {
        return withIdentifier(identifier.withAddressRange(ar));
    }

    public Rule withRequest(String request) {
        return withIdentifier(identifier.withRequest(request));
    }

    public Rule withSubfield(String subfield) {
        return withIdentifier(identifier.withSubfield(subfield));
    }

    public Rule withWorkspace(String workspace) {
        return withIdentifier(identifier.withWorkspace(workspace));
    }

    public Rule withLayer(String layer) {
        return withIdentifier(identifier.withLayer(layer));
    }

    public Rule withAccess(GrantType access) {
        return withIdentifier(identifier.withAccess(access));
    }

    public static Rule allow() {
        return Rule.builder().build().withAccess(GrantType.ALLOW);
    }

    public static Rule deny() {
        return Rule.builder().build().withAccess(GrantType.DENY);
    }

    public static Rule limit() {
        return Rule.builder().build().withAccess(GrantType.LIMIT);
    }
}
