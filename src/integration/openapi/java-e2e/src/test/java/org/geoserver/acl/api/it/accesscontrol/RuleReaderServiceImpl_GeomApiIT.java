/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.acl.api.it.accesscontrol;

import org.geoserver.acl.api.it.support.ClientContextSupport;
import org.geoserver.acl.api.it.support.IntegrationTestsApplication;
import org.geoserver.acl.api.it.support.ServerContextSupport;
import org.geoserver.acl.authorization.AbstractRuleReaderServiceImpl_GeomTest;
import org.geoserver.acl.authorization.RuleReaderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/**
 * RuleReaderServiceImpl end to end integration test for {@link RuleReaderService#getAccessInfo}
 * calls involving geometry operations.
 *
 * @see RuleReaderServiceImplApiIT
 * @see AbstractRuleReaderServiceImpl_GeomTest
 */
@DirtiesContext
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "geoserver.acl.jpa.show-sql=false",
            "geoserver.acl.jpa.properties.hibernate.hbm2ddl.auto=create",
            "geoserver.acl.datasource.url=jdbc:h2:mem:geoserver-acl"
        },
        classes = {IntegrationTestsApplication.class})
public class RuleReaderServiceImpl_GeomApiIT extends AbstractRuleReaderServiceImpl_GeomTest {

    private @Autowired ServerContextSupport serverContext;
    private @LocalServerPort int serverPort;

    private ClientContextSupport clientContext;

    @BeforeEach
    void setUp() throws Exception {
        clientContext =
                new ClientContextSupport()
                        // logging breaks client exception handling, only enable if need to see the
                        // request/response bodies
                        .log(false)
                        .serverPort(serverPort)
                        .setUp();
        super.ruleAdminService = clientContext.getRuleAdminServiceClient();
        super.adminruleAdminService = clientContext.getAdminRuleAdminServiceClient();
        super.ruleReaderService = clientContext.getRuleReaderServiceImpl();

        serverContext.setUp();
    }

    @AfterEach
    void tearDown() {
        clientContext.close();
    }
}
