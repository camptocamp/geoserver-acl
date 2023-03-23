/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 * Original from GeoFence 3.6 under GPL 2.0 license
 */

package org.geoserver.acl.authorization;

import static org.geoserver.acl.model.rules.GrantType.ALLOW;
import static org.geoserver.acl.model.rules.GrantType.DENY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.geoserver.acl.adminrules.AdminRuleAdminService;
import org.geoserver.acl.adminrules.MemoryAdminRuleRepository;
import org.geoserver.acl.model.adminrules.AdminRule;
import org.geoserver.acl.model.authorization.AccessInfo;
import org.geoserver.acl.model.authorization.AccessRequest;
import org.geoserver.acl.model.authorization.AdminAccessRequest;
import org.geoserver.acl.model.authorization.AuthorizationService;
import org.geoserver.acl.model.filter.RuleFilter;
import org.geoserver.acl.model.filter.RuleQuery;
import org.geoserver.acl.model.filter.predicate.SpecialFilterType;
import org.geoserver.acl.model.rules.IPAddressRange;
import org.geoserver.acl.model.rules.LayerAttribute;
import org.geoserver.acl.model.rules.LayerDetails;
import org.geoserver.acl.model.rules.Rule;
import org.geoserver.acl.rules.MemoryRuleRepository;
import org.geoserver.acl.rules.RuleAdminService;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link AuthorizationService} integration/conformance test
 *
 * <p>Concrete implementations must supply the required services in {@link ServiceTestBase}
 *
 * @author Emanuele Tajariol (etj at geo-solutions.it) (originally as part of GeoFence)
 */
public class AuthorizationServiceImplTest extends ServiceTestBase {

    @Override
    protected RuleAdminService getRuleAdminService() {
        return new RuleAdminService(new MemoryRuleRepository());
    }

    @Override
    protected AdminRuleAdminService getAdminRuleAdminService() {
        return new AdminRuleAdminService(new MemoryAdminRuleRepository());
    }

    @Override
    protected AuthorizationService getAuthorizationService() {
        return new AuthorizationServiceImpl(super.adminruleAdminService, super.ruleAdminService);
    }

    @Test
    public void testGetRulesForUsersAndGroup() {

        assertEquals(0, ruleAdminService.count(RuleFilter.any()));

        final AccessRequest u1 = createRequest("TestUser1", "p1");
        final AccessRequest u2 = createRequest("TestUser2", "p2");
        final AccessRequest u3 = createRequest("TestUser3", "g3a", "g3b");

        insert(
                Rule.allow()
                        .withPriority(10)
                        .withUsername(u1.getUser())
                        .withRolename("p1")
                        .withService("s1")
                        .withRequest("r1")
                        .withWorkspace("w1")
                        .withLayer("l1"));
        insert(
                Rule.allow()
                        .withPriority(20)
                        .withUsername(u2.getUser())
                        .withRolename("p2")
                        .withService("s1")
                        .withRequest("r2")
                        .withWorkspace("w2")
                        .withLayer("l2"));
        insert(
                Rule.allow()
                        .withPriority(30)
                        .withUsername(u1.getUser())
                        .withRolename("p1")
                        .withService("s3")
                        .withRequest("r3")
                        .withWorkspace("w3")
                        .withLayer("l3"));
        insert(Rule.allow().withPriority(40).withUsername(u1.getUser()).withRolename("p1"));
        insert(Rule.allow().withPriority(50).withRolename("g3a"));
        insert(Rule.allow().withPriority(60).withRolename("g3b"));

        assertEquals(3, getMatchingRules(u1, "Z", "*", "*", "*", "*", "*").size());
        assertEquals(3, getMatchingRules("*", "p1", "Z", "*", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules(u1, "Z", "*", null, null, null, null).size());
        assertEquals(0, getMatchingRules("*", "Z", "Z", "*", null, null, null, null).size());
        assertEquals(1, getMatchingRules(u1, "Z", "*", null, null, null, null).size());
        assertEquals(1, getMatchingRules(u1, "Z", "*", null, null, null, null).size());
        assertEquals(1, getMatchingRules(u2, "Z", "*", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "p2", "Z", "*", "*", "*", "*", "*").size());
        assertEquals(2, getMatchingRules(u1, "Z", "*", "s1", "*", "*", "*").size());
        assertEquals(2, getMatchingRules("*", "p1", "Z", "*", "s1", "*", "*", "*").size());
        assertEquals(2, getMatchingRules(u3.getUser(), "*", "Z", "*", "s1", "*", "*", "*").size());
    }

    @Test
    public void testGetRulesForGroupOnly() {

        assertEquals(0, ruleAdminService.count(RuleFilter.any()));

        Rule r1 =
                Rule.allow()
                        .withPriority(10)
                        .withRolename("p1")
                        .withService("s1")
                        .withRequest("r1")
                        .withWorkspace("w1")
                        .withLayer("l1");
        Rule r2 =
                Rule.allow()
                        .withPriority(20)
                        .withRolename("p2")
                        .withService("s1")
                        .withRequest("r2")
                        .withWorkspace("w2")
                        .withLayer("l2");
        Rule r3 =
                Rule.allow()
                        .withPriority(30)
                        .withRolename("p1")
                        .withService("s3")
                        .withRequest("r3")
                        .withWorkspace("w3")
                        .withLayer("l3");
        Rule r4 = Rule.allow().withPriority(40).withRolename("p1");

        r1 = insert(r1);
        r2 = insert(r2);
        r3 = insert(r3);
        r4 = insert(r4);

        assertEquals(4, getMatchingRules("*", "*", "*", "*", "*", "*", "*", "*").size());
        assertEquals(3, getMatchingRules("*", "*", "*", "*", "s1", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "*", "*", "*", "ZZ", "*", "*", "*").size());

        assertEquals(3, getMatchingRules("*", "p1", "*", "*", "*", "*", "*", "*").size());
        assertEquals(2, getMatchingRules("*", "p1", "*", "*", "s1", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "p1", "*", "*", "ZZ", "*", "*", "*").size());

        assertEquals(1, getMatchingRules("*", "p2", "*", "*", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "p2", "*", "*", "s1", "*", "*", "*").size());
        assertEquals(0, getMatchingRules("*", "p2", "*", "*", "ZZ", "*", "*", "*").size());

        AccessRequest req = AccessRequest.builder().roles(Set.of("p1")).build();
        assertEquals(3, authorizationService.getMatchingRules(req).size());

        req = AccessRequest.builder().service("s3").build();
        assertEquals(2, authorizationService.getMatchingRules(req).size());
    }

    @Test
    public void testGetInfo() {
        assertEquals(0, ruleAdminService.count(new RuleFilter(SpecialFilterType.ANY)));

        List<Rule> rules = new ArrayList<>();

        rules.add(insert(Rule.allow().withPriority(100 + rules.size()).withService("WCS")));
        rules.add(
                insert(
                        Rule.allow()
                                .withPriority(100 + rules.size())
                                .withService("s1")
                                .withRequest("r2")
                                .withWorkspace("w2")
                                .withLayer("l2")));
        rules.add(
                insert(
                        Rule.allow()
                                .withPriority(100 + rules.size())
                                .withService("s3")
                                .withRequest("r3")
                                .withWorkspace("w3")
                                .withLayer("l3")));
        rules.add(insert(Rule.deny().withPriority(100 + rules.size())));

        assertEquals(4, ruleAdminService.count(new RuleFilter(SpecialFilterType.ANY)));

        //        RuleFilter baseFilter = new RuleFilter(SpecialFilterType.ANY);
        //        baseFilter.setUser("u0");
        //        baseFilter.setRole("p0");
        //        baseFilter.setInstance("i0");
        //        baseFilter.setService("WCS");
        //        baseFilter.setRequest(SpecialFilterType.ANY);
        //        baseFilter.setWorkspace("W0");
        //        baseFilter.setLayer("l0");
        //        AccessRequest req = AccessRequest.builder().user(req).filter(baseFilter).build();

        AccessRequest req =
                createRequest("u0", "p0")
                        .withInstance("i0")
                        .withService("WCS")
                        .withRequest(null)
                        .withWorkspace("W0")
                        .withLayer("l0");

        {
            //            RuleFilter ruleFilter = new RuleFilter(baseFilter);
            //            ruleFilter.setUser(SpecialFilterType.ANY);
            assertEquals(2, authorizationService.getMatchingRules(req.withUser(null)).size());
            assertEquals(ALLOW, authorizationService.getAccessInfo(req.withUser(null)).getGrant());
        }
        {
            //            RuleFilter ruleFilter = new RuleFilter(baseFilter);
            //            ruleFilter.setRole(SpecialFilterType.ANY);

            assertEquals(2, authorizationService.getMatchingRules(req.withRoles(Set.of())).size());
            assertEquals(
                    ALLOW, authorizationService.getAccessInfo(req.withRoles(Set.of())).getGrant());
        }
        {
            //            RuleFilter ruleFilter = new RuleFilter(baseFilter);
            //            ruleFilter.setUser(SpecialFilterType.ANY);
            //            ruleFilter.setService("UNMATCH");

            AccessRequest unmatch = req.withUser(null).withService("UNMATCH");
            assertEquals(1, authorizationService.getMatchingRules(unmatch).size());
            assertEquals(DENY, authorizationService.getAccessInfo(unmatch).getGrant());
        }
        {
            //            RuleFilter ruleFilter = new RuleFilter(baseFilter);
            //            ruleFilter.setRole(SpecialFilterType.ANY);
            //            ruleFilter.setService("UNMATCH");
            AccessRequest unmatch = req.withRoles(Set.of()).withService("UNMATCH");

            assertEquals(1, authorizationService.getMatchingRules(unmatch).size());
            assertEquals(DENY, authorizationService.getAccessInfo(unmatch).getGrant());
        }
    }

    @Test
    public void testResolveLazy() {
        assertEquals(0, ruleAdminService.count());

        List<Rule> rules = new ArrayList<>();

        rules.add(insert(Rule.allow().withPriority(100 + rules.size()).withService("WCS")));
        rules.add(
                insert(
                        Rule.allow()
                                .withPriority(100 + rules.size())
                                .withService("s1")
                                .withRequest("r2")
                                .withWorkspace("w2")
                                .withLayer("l2")));

        LayerDetails details = LayerDetails.builder().build();
        ruleAdminService.setLayerDetails(rules.get(1).getId(), details);

        assertEquals(2, ruleAdminService.count(new RuleFilter(SpecialFilterType.ANY)));

        AccessInfo accessInfo;

        {
            final AccessRequest req = AccessRequest.builder().service("s1").layer("l2").build();
            RuleQuery<RuleFilter> query = RuleQuery.of(new RuleFilter(SpecialFilterType.ANY));

            assertEquals(2, ruleAdminService.getAll(query).count());
            List<Rule> matchingRules = authorizationService.getMatchingRules(req);

            assertEquals(1, matchingRules.size());
            accessInfo = authorizationService.getAccessInfo(req);
            assertEquals(ALLOW, accessInfo.getGrant());
            assertNull(accessInfo.getArea());
        }
    }

    @Test
    public void testNoDefault() {

        assertEquals(0, ruleAdminService.count(new RuleFilter(SpecialFilterType.ANY)));

        insert(Rule.allow().withService("WCS"));

        assertEquals(1, getMatchingRules("u0", "*", "i0", null, "WCS", null, "W0", "l0").size());
        assertEquals(
                ALLOW, getAccessInfo("u0", "*", "i0", null, "WCS", null, "W0", "l0").getGrant());

        assertEquals(1, getMatchingRules("*", "p0", "i0", null, "WCS", null, "W0", "l0").size());
        assertEquals(
                ALLOW, getAccessInfo("*", "p0", "i0", null, "WCS", null, "W0", "l0").getGrant());

        assertEquals(
                0, getMatchingRules("u0", "*", "i0", null, "UNMATCH", null, "W0", "l0").size());
        assertEquals(
                DENY, getAccessInfo("u0", "*", "i0", null, "UNMATCH", null, "W0", "l0").getGrant());

        assertEquals(
                0, getMatchingRules("*", "p0", "i0", null, "UNMATCH", null, "W0", "l0").size());
        assertEquals(
                DENY, getAccessInfo("*", "p0", "i0", null, "UNMATCH", null, "W0", "l0").getGrant());
    }

    @Test
    public void testGroups() {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest u1 = createRequest("u1", "p1");
        final AccessRequest u2 = createRequest("u2", "p2");

        List<Rule> rules = new ArrayList<>();

        rules.add(
                insert(
                        Rule.allow()
                                .withPriority(10 + rules.size())
                                .withRolename("p1")
                                .withService("s1")
                                .withRequest("r1")
                                .withWorkspace("w1")
                                .withLayer("l1")));
        rules.add(insert(Rule.deny().withPriority(10 + rules.size()).withRolename("p1")));

        // LOGGER.info("SETUP ENDED, STARTING TESTS");
        // ===

        assertEquals(rules.size(), ruleAdminService.count());

        {
            //            RuleFilter filter = new
            // RuleFilter(SpecialFilterType.ANY).setUser(u1.getName());
            //            AccessRequest request =
            // AccessRequest.builder().user(u1).filter(filter).build();

            AccessRequest request = u1;
            assertEquals(2, authorizationService.getMatchingRules(request).size());

            request = u1.withService("s1");
            assertEquals(2, authorizationService.getMatchingRules(request).size());
            assertEquals(ALLOW, authorizationService.getAccessInfo(request).getGrant());

            request = u1.withService("s2");
            assertEquals(1, authorizationService.getMatchingRules(request).size());
            assertEquals(DENY, authorizationService.getAccessInfo(request).getGrant());
        }

        {
            //            RuleFilter filter = new
            // RuleFilter(SpecialFilterType.ANY).setUser(u2.getName());
            //            AccessRequest request =
            // AccessRequest.builder().user(u2).filter(filter).build();
            AccessRequest request = u2;
            assertEquals(0, authorizationService.getMatchingRules(request).size());
            assertEquals(DENY, authorizationService.getAccessInfo(request).getGrant());
        }
    }

    @Test
    public void testGroupOrder01() throws UnknownHostException {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest req1 = createRequest("u1", "p1");
        final AccessRequest req2 = createRequest("u2", "p2");

        List<Rule> rules = new ArrayList<Rule>();
        rules.add(insert(Rule.allow().withPriority(10 + rules.size()).withRolename("p1")));
        rules.add(insert(Rule.deny().withPriority(10 + rules.size()).withRolename("p2")));

        // LOGGER.info("SETUP ENDED, STARTING TESTS");
        // ===

        assertEquals(rules.size(), ruleAdminService.count());

        //        RuleFilter filterU1 = new RuleFilter(SpecialFilterType.ANY).setUser(u1.getName());
        //        AccessRequest req1 = AccessRequest.builder().user(u1).filter(filterU1).build();
        //
        //        RuleFilter filterU2 = new RuleFilter(SpecialFilterType.ANY).setUser(u2.getName());
        //        AccessRequest req2 = AccessRequest.builder().user(u2).filter(filterU2).build();

        assertEquals(1, authorizationService.getMatchingRules(req1).size());
        assertEquals(1, authorizationService.getMatchingRules(req2).size());

        assertEquals(ALLOW, authorizationService.getAccessInfo(req1).getGrant());
        assertEquals(DENY, authorizationService.getAccessInfo(req2).getGrant());
    }

    @Test
    public void testGroupOrder02() {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest req1 = createRequest("u1", "p1");
        final AccessRequest req2 = createRequest("u2", "p2");

        List<Rule> rules = new ArrayList<Rule>();
        rules.add(insert(Rule.deny().withPriority(10 + rules.size()).withRolename("p2")));
        rules.add(insert(Rule.allow().withPriority(10 + rules.size()).withRolename("p1")));

        // LOGGER.info("SETUP ENDED, STARTING TESTS");
        // ===

        assertEquals(rules.size(), ruleAdminService.count());

        //        RuleFilter filterU1 = new RuleFilter(SpecialFilterType.ANY).setUser(u1.getName());
        //        AccessRequest req1 = AccessRequest.builder().user(u1).filter(filterU1).build();
        //
        //        RuleFilter filterU2 = new RuleFilter(SpecialFilterType.ANY).setUser(u2.getName());
        //        AccessRequest req2 = AccessRequest.builder().user(u2).filter(filterU2).build();

        assertEquals(1, authorizationService.getMatchingRules(req1).size());
        assertEquals(1, authorizationService.getMatchingRules(req2).size());

        assertEquals(ALLOW, authorizationService.getAccessInfo(req1).getGrant());
        assertEquals(DENY, authorizationService.getAccessInfo(req2).getGrant());
    }

    @Test
    public void testAttrib() {
        assertEquals(0, ruleAdminService.count());
        final AccessRequest req1 = createRequest("u1", "g1");
        final AccessRequest req2 = createRequest("u2", "g2");
        final AccessRequest req12 = createRequest("u12", "g1", "g2");
        final AccessRequest req13 = createRequest("u13", "g1", "g3");

        {
            Rule r1 = insert(Rule.allow().withRolename("g1").withLayer("l1"));

            Set<LayerAttribute> atts1 =
                    Set.of(
                            LayerAttribute.none().withName("att1").withDataType("String"),
                            LayerAttribute.read().withName("att2").withDataType("String"),
                            LayerAttribute.write().withName("att3").withDataType("String"));

            LayerDetails d1 =
                    LayerDetails.builder()
                            .allowedStyles(Set.of("style01", "style02"))
                            .attributes(atts1)
                            .build();
            ruleAdminService.setLayerDetails(r1.getId(), d1);

            Rule r2 = insert(Rule.allow().withRolename("g2").withLayer("l1"));

            Set<LayerAttribute> atts2 =
                    Set.of(
                            LayerAttribute.read().withName("att1").withDataType("String"),
                            LayerAttribute.write().withName("att2").withDataType("String"),
                            LayerAttribute.none().withName("att3").withDataType("String"));

            LayerDetails d2 =
                    LayerDetails.builder()
                            .allowedStyles(Set.of("style02", "style03"))
                            .attributes(atts2)
                            .build();

            ruleAdminService.setLayerDetails(r2.getId(), d2);

            Rule r3 = insert(Rule.allow().withRolename("g3").withLayer("l1"));
            LayerDetails d3 = LayerDetails.builder().build();
            ruleAdminService.setLayerDetails(r3.getId(), d3);

            insert(Rule.deny().withRolename("g4").withLayer("l1"));
        }

        // LOGGER.info("SETUP ENDED, STARTING
        // TESTS========================================");

        assertEquals(4, ruleAdminService.count());

        // ===

        // TEST u1
        {
            //            RuleFilter filterU1 = new RuleFilter(SpecialFilterType.ANY).setUser("u1");
            //            AccessRequest request =
            // AccessRequest.builder().user(req1).filter(filterU1).build();
            AccessRequest request = req1;
            assertEquals(1, authorizationService.getMatchingRules(request).size());

            AccessInfo accessInfo = authorizationService.getAccessInfo(request);
            assertEquals(ALLOW, accessInfo.getGrant());
        }

        // TEST u2
        {
            //            RuleFilter filter = new
            // RuleFilter(SpecialFilterType.ANY).setUser("u2").setLayer("l1");
            //            AccessRequest request =
            // AccessRequest.builder().user(req2).filter(filter).build();
            AccessRequest request = req2;

            assertEquals(1, authorizationService.getMatchingRules(request).size());

            AccessInfo accessInfo = authorizationService.getAccessInfo(request);
            assertEquals(ALLOW, accessInfo.getGrant());
            assertNotNull(accessInfo.getAttributes());
            assertEquals(3, accessInfo.getAttributes().size());
            assertEquals(
                    Set.of(
                            LayerAttribute.read().withName("att1").withDataType("String"),
                            LayerAttribute.write().withName("att2").withDataType("String"),
                            LayerAttribute.none().withName("att3").withDataType("String")),
                    accessInfo.getAttributes());

            assertEquals(2, accessInfo.getAllowedStyles().size());
        }

        // TEST u3
        // merging attributes at higher access level
        // merging styles
        {
            //            RuleFilter filter =
            //                    new
            // RuleFilter(SpecialFilterType.ANY).setUser(req12.getName()).setLayer("l1");
            //            AccessRequest request =
            // AccessRequest.builder().user(req12).filter(filter).build();
            AccessRequest request = req12.withLayer("l1");
            assertEquals(2, authorizationService.getMatchingRules(request).size());

            AccessInfo accessInfo = authorizationService.getAccessInfo(request);
            assertEquals(ALLOW, accessInfo.getGrant());
            assertNotNull(accessInfo.getAttributes());
            assertEquals(3, accessInfo.getAttributes().size());
            assertEquals(
                    Set.of(
                            LayerAttribute.read().withName("att1").withDataType("String"),
                            LayerAttribute.write().withName("att2").withDataType("String"),
                            LayerAttribute.write().withName("att3").withDataType("String")),
                    accessInfo.getAttributes());

            assertEquals(3, accessInfo.getAllowedStyles().size());
        }

        // TEST u4
        // merging attributes to full access
        // unconstraining styles

        {
            //            RuleFilter filter;
            //            filter = new
            // RuleFilter(SpecialFilterType.ANY).setUser(req13.getName()).setLayer("l1");
            //            AccessRequest request =
            // AccessRequest.builder().user(req13).filter(filter).build();
            AccessRequest request = req13;

            assertEquals(2, authorizationService.getMatchingRules(request).size());

            AccessInfo accessInfo = authorizationService.getAccessInfo(request);
            assertEquals(ALLOW, accessInfo.getGrant());
            // LOGGER.info("attributes: " + accessInfo.getAttributes());
            assertTrue(accessInfo.getAttributes().isEmpty());
            // assertEquals(3, accessInfo.getAttributes().size());
            // assertEquals(
            // new HashSet(Arrays.asList(
            // new LayerAttribute("att1", "String", AccessType.READONLY),
            // new LayerAttribute("att2", "String", AccessType.READWRITE),
            // new LayerAttribute("att3", "String", AccessType.READWRITE))),
            // accessInfo.getAttributes());

            assertTrue(accessInfo.getAllowedStyles().isEmpty());
        }
    }

    /** Added for issue #23 */
    @Test
    public void testNullAllowableStyles() {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest request = createRequest("u1", "g1", "g2");

        // no details for first rule
        {
            insert(Rule.allow().withPriority(30).withRolename("g2").withLayer("l1"));
        }
        // some allowed styles for second rule
        {
            Rule r1 = insert(Rule.allow().withPriority(40).withRolename("g1").withLayer("l1"));

            LayerDetails d1 =
                    LayerDetails.builder().allowedStyles(Set.of("style01", "style02")).build();

            ruleAdminService.setLayerDetails(r1.getId(), d1);
        }

        // LOGGER.info("SETUP ENDED, STARTING
        // TESTS========================================");

        assertEquals(2, ruleAdminService.count());

        // ===

        // TEST u1
        {
            //            RuleFilter filterU1 = new
            // RuleFilter(SpecialFilterType.ANY).setUser(u1.getName());
            //            AccessRequest request =
            // AccessRequest.builder().user(u1).filter(filterU1).build();

            // LOGGER.info("getMatchingRules ========================================");
            assertEquals(2, authorizationService.getMatchingRules(request).size());

            // LOGGER.info("getAccessInfo ========================================");
            AccessInfo accessInfo = authorizationService.getAccessInfo(request);
            assertEquals(ALLOW, accessInfo.getGrant());

            assertTrue(accessInfo.getAllowedStyles().isEmpty());
        }
    }

    @Test
    public void testIPAddress() {

        RuleFilter filter = new RuleFilter(SpecialFilterType.ANY);
        assertEquals(0, ruleAdminService.count(filter));

        IPAddressRange ip10 = IPAddressRange.fromCidrSignature("10.10.100.0/24");
        IPAddressRange ip192 = IPAddressRange.fromCidrSignature("192.168.0.0/16");

        Rule r1 =
                Rule.allow()
                        .withPriority(10)
                        .withRolename("g1")
                        .withService("s1")
                        .withRequest("r1")
                        .withWorkspace("w1")
                        .withLayer("l1")
                        .withAddressRange(ip10);
        Rule r2 =
                Rule.allow()
                        .withPriority(20)
                        .withRolename("g2")
                        .withService("s1")
                        .withRequest("r2")
                        .withWorkspace("w2")
                        .withLayer("l2")
                        .withAddressRange(ip10);
        Rule r3 =
                Rule.allow()
                        .withPriority(30)
                        .withRolename("g1")
                        .withService("s3")
                        .withRequest("r3")
                        .withWorkspace("w3")
                        .withLayer("l3")
                        .withAddressRange(ip192);
        Rule r4 = Rule.allow().withPriority(40).withRolename("g1");

        r1 = insert(r1);
        r2 = insert(r2);
        r3 = insert(r3);
        r4 = insert(r4);

        // test without address filtering

        assertEquals(4, getMatchingRules("*", "*", "*", "*", "*", "*", "*", "*").size());
        assertEquals(3, getMatchingRules("*", "g1", "*", "*", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "g2", "*", "*", "*", "*", "*", "*").size());
        assertEquals(2, getMatchingRules("*", "g1", "*", "*", "s1", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "*", "*", "*", "ZZ", "*", "*", "*").size());

        // test with address filtering
        assertEquals(3, getMatchingRules("*", "*", "*", "10.10.100.4", "*", "*", "*", "*").size());
        assertEquals(2, getMatchingRules("*", "g1", "*", "10.10.100.4", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "*", "*", "10.10.1.4", "*", "*", "*", "*").size());
        assertEquals(2, getMatchingRules("*", "*", "*", "192.168.1.1", "*", "*", "*", "*").size());
        assertEquals(1, getMatchingRules("*", "*", "*", null, "*", "*", "*", "*").size());

        List<Rule> matchingRules = getMatchingRules("*", "*", "*", "BAD", "*", "*", "*", "*");
        assertEquals(0, matchingRules.size());
    }

    @Test
    public void testGetRulesForUserOnly() {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest u1 = createRequest("TestUser1", "g1");

        insert(
                Rule.allow()
                        .withPriority(10)
                        .withRolename("g1")
                        .withService("s1")
                        .withRequest("r1")
                        .withWorkspace("w1")
                        .withLayer("l1"));
        insert(
                Rule.allow()
                        .withPriority(20)
                        .withRolename("g2")
                        .withService("s2")
                        .withRequest("r2")
                        .withWorkspace("w2")
                        .withLayer("l2"));
        insert(
                Rule.allow()
                        .withPriority(30)
                        .withRolename("g1")
                        .withService("s3")
                        .withRequest("r3")
                        .withWorkspace("w3")
                        .withLayer("l3"));
        insert(Rule.allow().withPriority(40).withRolename("g1"));
        insert(Rule.allow().withPriority(50).withRolename("g3a"));
        insert(Rule.allow().withPriority(60).withRolename("g3b"));

        AccessRequest request = u1;
        assertEquals(3, authorizationService.getMatchingRules(request).size());

        request = u1.withService("s1");
        assertEquals(2, authorizationService.getMatchingRules(request).size());

        request = u1.withService("s3");
        assertEquals(2, authorizationService.getMatchingRules(request).size());

        request = AccessRequest.builder().user("anonymous").roles(Set.of()).service(null).build();
        assertEquals(0, authorizationService.getMatchingRules(request).size());
    }

    @Test
    public void testAdminRules() {

        final AccessRequest request = createRequest("auth00").withWorkspace("w1");
        final AdminAccessRequest adminReq =
                AdminAccessRequest.builder().user("auth00").workspace("w1").build();

        insert(
                Rule.allow()
                        .withPriority(10)
                        .withUsername(request.getUser())
                        .withService("s1")
                        .withRequest("r1")
                        .withWorkspace("w1")
                        .withLayer("l1"));

        //        RuleFilter filter = new RuleFilter(SpecialFilterType.ANY, true);
        //        filter.setWorkspace("w1");

        AccessInfo accessInfo = authorizationService.getAccessInfo(request);
        assertEquals(ALLOW, accessInfo.getGrant());

        assertFalse(authorizationService.getAdminAuthorization(adminReq).isAdmin());

        // let's add a USER adminrule

        insert(AdminRule.user().withPriority(20).withUsername(request.getUser()));

        accessInfo = authorizationService.getAccessInfo(request);
        assertEquals(ALLOW, accessInfo.getGrant());
        assertFalse(authorizationService.getAdminAuthorization(adminReq).isAdmin());

        // let's add an ADMIN adminrule on workspace w1

        adminruleAdminService.insert(
                AdminRule.admin()
                        .withPriority(10)
                        .withUsername(request.getUser())
                        .withWorkspace(request.getWorkspace()));

        accessInfo = authorizationService.getAccessInfo(request);
        assertEquals(ALLOW, accessInfo.getGrant());
        assertTrue(authorizationService.getAdminAuthorization(adminReq).isAdmin());
    }

    // @Disabled
    @Test
    public void testMultiRoles() {
        assertEquals(0, ruleAdminService.count());

        final AccessRequest u1 = createRequest("TestUser1", "p1");
        final AccessRequest u2 = createRequest("TestUser2", "p2");
        final AccessRequest u3 = createRequest("TestUser3", "p1", "p2");

        insert(10, u1.getUser(), "p1", null, null, "s1", "r1", null, "w1", "l1", ALLOW);
        insert(20, u2.getUser(), "p2", null, null, "s1", "r2", null, "w2", "l2", ALLOW);
        insert(30, u1.getUser(), null, null, null, null, null, null, null, null, ALLOW);
        insert(40, u2.getUser(), null, null, null, null, null, null, null, null, ALLOW);
        insert(50, u3.getUser(), null, null, null, null, null, null, null, null, ALLOW);
        insert(51, u3.getUser(), "p1", null, null, null, null, null, null, null, ALLOW);
        insert(52, u3.getUser(), "p2", null, null, null, null, null, null, null, ALLOW);
        insert(60, null, "p1", null, null, null, null, null, null, null, ALLOW);
        insert(70, null, "p2", null, null, null, null, null, null, null, ALLOW);
        insert(80, null, "p3", null, null, null, null, null, null, null, ALLOW);
        insert(901, u1.getUser(), "p2", null, null, null, null, null, null, null, ALLOW);
        insert(902, u2.getUser(), "p1", null, null, null, null, null, null, null, ALLOW);
        insert(999, null, null, null, null, null, null, null, null, null, ALLOW);

        assertRules("*", "*", 10, 20, 30, 40, 50, 51, 52, 60, 70, 80, 901, 902, 999);
        assertRules("*", null, 30, 40, 50, 999);
        assertRules("*", "NO", 30, 40, 50, 999);
        assertRules("*", "p1", 10, 30, 40, 50, 51, 60, 902, 999);
        assertRules("*", "p1,NO", 10, 30, 40, 50, 51, 60, 902, 999);
        assertRules("*", "p1,p2", 10, 20, 30, 40, 50, 51, 52, 60, 70, 901, 902, 999);
        assertRules("*", "p1,p2,NO", 10, 20, 30, 40, 50, 51, 52, 60, 70, 901, 902, 999);

        assertRules((String) null, "*", 60, 70, 80, 999);
        assertRules((String) null, null, 999);
        assertRules((String) null, "NO", 999);
        assertRules((String) null, "p1", 60, 999);
        assertRules((String) null, "p1,NO", 60, 999);
        assertRules((String) null, "p1,p2", 60, 70, 999);
        assertRules((String) null, "p1,p2,NO", 60, 70, 999);

        assertRules("NO", null, 999);
        assertRules("NO", null, 999);
        assertRules("NO", "NO", 999);
        assertRules("NO", "p1", 60, 999);
        assertRules("NO", "p1NO", 999);
        assertRules("NO", "p1,p2", 60, 70, 999);
        assertRules("NO", "p1,p2,NO", 60, 70, 999);

        //        assertRules(u1, "*", 10, 30, 60, 999);
        assertRules(u1, 10, 30, 60, 999);
        assertRules(u1.withRoles(Set.of()), 30, 999);
        assertRules(u1.withRoles(Set.of("NO")), 30, 999);
        assertRules(u1.withRoles(Set.of("p1")), 10, 30, 60, 999);
        assertRules(u1.withRoles(Set.of("p1", "NO")), 10, 30, 60, 999);
        assertRules(u1.withRoles(Set.of("p1", "p2")), 10, 30, 60, 70, 901, 999);
        assertRules(u1.withRoles(Set.of("p1", "p2", "NO")), 10, 30, 60, 70, 901, 999);

        assertRules(u3.withRoles(Set.of("*")), 50, 51, 52, 60, 70, 80, 999);
        assertRules(u3.withRoles(Set.of()), 50, 999);
        assertRules(u3.withRoles(Set.of("NO")), 50, 999);
        assertRules(u3.withRoles(Set.of("p1")), 50, 51, 60, 999);
        assertRules(u3.withRoles(Set.of("p2")), 50, 52, 70, 999);
        assertRules(u3.withRoles(Set.of("p1", "NO")), 50, 51, 60, 999);
        assertRules(u3.withRoles(Set.of("p1", "p2")), 50, 51, 52, 60, 70, 999);
        assertRules(u3.withRoles(Set.of("p1", "p2", "p3")), 50, 51, 52, 60, 70, 80, 999);
        assertRules(u3.withRoles(Set.of("p1", "p2", "NO")), 50, 51, 52, 60, 70, 999);
    }

    private void assertRules(String userName, String groupNames, Integer... expectedPriorities) {

        String[] groups = groupNames == null ? new String[0] : groupNames.split(",");
        AccessRequest request = createRequest(userName, groups);
        assertRules(request, expectedPriorities);
    }

    private void assertRules(AccessRequest request, Integer... expectedPriorities) {
        List<Rule> rules = authorizationService.getMatchingRules(request);

        List<Long> pri =
                rules.stream().map(r -> r.getPriority()).sorted().collect(Collectors.toList());
        List<Long> exp =
                Arrays.asList(expectedPriorities).stream()
                        .map(i -> i.longValue())
                        .collect(Collectors.toList());
        assertEquals(exp, pri, "Bad rule set selected for filter " + request);
    }

    private List<Rule> getMatchingRules(
            String userName,
            String profileName,
            String instanceName,
            String sourceAddress,
            String service,
            String request,
            String workspace,
            String layer) {

        return getMatchingRules(
                createRequest(userName, profileName),
                instanceName,
                sourceAddress,
                service,
                request,
                workspace,
                layer);
    }

    private List<Rule> getMatchingRules(
            AccessRequest baseRequest,
            String instanceName,
            String sourceAddress,
            String service,
            String request,
            String workspace,
            String layer) {

        AccessRequest req =
                baseRequest
                        .withInstance(instanceName)
                        .withSourceAddress(sourceAddress)
                        .withService(service)
                        .withRequest(request)
                        .withWorkspace(workspace)
                        .withLayer(layer);

        return authorizationService.getMatchingRules(req);
    }

    private AccessInfo getAccessInfo(
            String userName,
            String roleName,
            String instanceName,
            String sourceAddress,
            String service,
            String request,
            String workspace,
            String layer) {

        AccessRequest req =
                createRequest(userName, roleName)
                        .withInstance(instanceName)
                        .withSourceAddress(sourceAddress)
                        .withService(service)
                        .withRequest(request)
                        .withWorkspace(workspace)
                        .withLayer(layer);

        return authorizationService.getAccessInfo(req);
    }
}
