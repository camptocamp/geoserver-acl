/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoFence 3.6 under GPL 2.0 license
 */

package org.geoserver.acl.model.authorization;

import org.geoserver.acl.model.rules.Rule;

import java.util.List;

/**
 * Operations on
 *
 * @author Emanuele Tajariol (etj at geo-solutions.it) (originally as part of GeoFence)
 */
public interface AuthorizationService {

    /** Return info on resource accessibility. */
    AccessInfo getAccessInfo(AccessRequest request);

    /**
     * info about admin authorization on a given workspace.
     *
     * <p>Returned AdminAccessInfo will always be ALLOW, with the computed adminRights.
     */
    AdminAccessInfo getAdminAuthorization(AdminAccessRequest request);

    /**
     * Return the unprocessed {@link Rule} list matching a given filter, sorted by priority.
     *
     * <p>Use {@link getAccessInfo(RuleFilter) getAccessInfo(RuleFilter)} if you need the resulting
     * coalesced access info.
     */
    List<Rule> getMatchingRules(AccessRequest request);
}
