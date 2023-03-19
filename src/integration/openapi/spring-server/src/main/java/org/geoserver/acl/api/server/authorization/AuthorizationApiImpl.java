/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.acl.api.server.authorization;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.geoserver.acl.api.model.AccessInfo;
import org.geoserver.acl.api.model.AccessRequest;
import org.geoserver.acl.api.model.Rule;
import org.geoserver.acl.api.server.AuthorizationApiDelegate;
import org.geoserver.acl.api.server.support.AuthorizationApiSupport;
import org.geoserver.acl.model.authorization.AuthorizationService;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AuthorizationApiImpl implements AuthorizationApiDelegate {

    private final @NonNull AuthorizationService service;
    private final @NonNull AuthorizationApiSupport support;

    @Override
    public ResponseEntity<AccessInfo> getAccessInfo(AccessRequest accessRequest) {
        org.geoserver.acl.model.authorization.AccessRequest modelRequest =
                support.toModel(accessRequest);
        org.geoserver.acl.model.authorization.AccessInfo modelResponse =
                service.getAccessInfo(modelRequest);
        AccessInfo apiResponse = support.toApi(modelResponse);
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<AccessInfo> getAdminAuthorization(AccessRequest accessRequest) {
        org.geoserver.acl.model.authorization.AccessRequest modelRequest =
                support.toModel(accessRequest);
        org.geoserver.acl.model.authorization.AccessInfo modelResponse =
                service.getAdminAuthorization(modelRequest);
        AccessInfo apiResponse = support.toApi(modelResponse);
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<List<Rule>> getMatchingRules(AccessRequest accessRequest) {
        org.geoserver.acl.model.authorization.AccessRequest modelRequest =
                support.toModel(accessRequest);
        List<org.geoserver.acl.model.rules.Rule> modelResponse =
                service.getMatchingRules(modelRequest);

        List<Rule> apiResponse =
                modelResponse.stream().map(support::toApi).collect(Collectors.toList());
        return ResponseEntity.ok(apiResponse);
    }
}
