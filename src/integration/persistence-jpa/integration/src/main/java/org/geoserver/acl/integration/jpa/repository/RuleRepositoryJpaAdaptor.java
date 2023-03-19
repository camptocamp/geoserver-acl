/* (c) 2023  Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.acl.integration.jpa.repository;

import static org.geoserver.acl.integration.jpa.mapper.RuleJpaMapper.decodeId;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;

import lombok.NonNull;

import org.geoserver.acl.integration.jpa.mapper.RuleJpaMapper;
import org.geoserver.acl.jpa.model.GrantType;
import org.geoserver.acl.jpa.model.LayerDetails;
import org.geoserver.acl.jpa.model.QRule;
import org.geoserver.acl.jpa.model.RuleIdentifier;
import org.geoserver.acl.jpa.repository.JpaRuleRepository;
import org.geoserver.acl.jpa.repository.TransactionReadOnly;
import org.geoserver.acl.jpa.repository.TransactionRequired;
import org.geoserver.acl.jpa.repository.TransactionSupported;
import org.geoserver.acl.model.filter.RuleFilter;
import org.geoserver.acl.model.filter.RuleQuery;
import org.geoserver.acl.model.filter.predicate.IPAddressRangeFilter;
import org.geoserver.acl.model.rules.InsertPosition;
import org.geoserver.acl.model.rules.Rule;
import org.geoserver.acl.model.rules.RuleLimits;
import org.geoserver.acl.rules.RuleIdentifierConflictException;
import org.geoserver.acl.rules.RuleRepository;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;

@TransactionSupported
public class RuleRepositoryJpaAdaptor implements RuleRepository {

    private final EntityManager em;

    private final JpaRuleRepository jparepo;
    private final RuleJpaMapper modelMapper;
    private final PredicateMapper queryMapper;

    private final PriorityResolver<org.geoserver.acl.jpa.model.Rule> priorityResolver;

    public RuleRepositoryJpaAdaptor(
            EntityManager em, JpaRuleRepository jparepo, RuleJpaMapper mapper) {
        Objects.requireNonNull(em);
        Objects.requireNonNull(jparepo);
        Objects.requireNonNull(mapper);
        this.em = em;
        this.modelMapper = mapper;
        this.jparepo = jparepo;
        this.queryMapper = new PredicateMapper();
        this.priorityResolver =
                new PriorityResolver<>(jparepo, org.geoserver.acl.jpa.model.Rule::getPriority);
    }

    @Override
    public Optional<Rule> findById(@NonNull String id) {
        return jparepo.findById(decodeId(id)).map(modelMapper::toModel);
    }

    @Override
    public Optional<Rule> findOneByPriority(long priority) {
        try {
            return jparepo.findOne(QRule.rule.priority.eq(priority)).map(modelMapper::toModel);
        } catch (IncorrectResultSizeDataAccessException e) {
            throw new IllegalStateException("There are multiple Rules with priority " + priority);
        }
    }

    @Override
    public int count() {
        return (int) jparepo.count();
    }

    @Override
    public int count(RuleFilter filter) {
        Optional<Predicate> predicate = queryMapper.toPredicate(filter);
        Long count = predicate.map(jparepo::count).orElseGet(jparepo::count);
        return count.intValue();
    }

    @Override
    public Stream<Rule> findAll() {
        return findAll(RuleQuery.of());
    }

    // @Override
    //	public Stream<Rule> findAllOld(@NonNull RuleQuery<RuleFilter> query) {
    //
    //		Predicate predicate = queryMapper.toPredicate(query);
    //		Pageable pageRequest = queryMapper.toPageable(query);
    //
    //		Page<org.geoserver.acl.jpa.model.Rule> page;
    //		if (predicate.isPresent()) {
    //			page = jparepo.findAllNaturalOrder(predicate.get(), pageRequest);
    //		} else {
    //			page = jparepo.findAllNaturalOrder(pageRequest);
    //		}
    //
    //		List<org.geoserver.acl.jpa.model.Rule> found = page.getContent();
    //		return found.stream().map(modelMapper::toModel).filter(filterByAddress(query.getFilter()));
    //	}

    @Override
    @TransactionReadOnly
    public Stream<Rule> findAll(@NonNull RuleQuery<RuleFilter> query) {

        Predicate predicate = queryMapper.toPredicate(query);
        final java.util.function.Predicate<? super Rule> postFilter =
                filterByAddress(query.getFilter());

        if (query.getNextCursor() != null) {
            Long nextId = decodeId(query.getNextCursor());
            predicate = QRule.rule.id.goe(nextId).and(predicate);
        }

        CloseableIterator<org.geoserver.acl.jpa.model.Rule> iterator = query(predicate);

        try (Stream<org.geoserver.acl.jpa.model.Rule> stream = stream(iterator)) {
            Stream<Rule> rules = stream.map(modelMapper::toModel).filter(postFilter);
            final Integer pageSize = query.getPageSize();
            if (null != pageSize) {
                rules = rules.limit(query.getPageSize());
            }
            return rules.collect(Collectors.toList()).stream();
        }
    }

    private CloseableIterator<org.geoserver.acl.jpa.model.Rule> query(Predicate predicate) {

        CloseableIterator<org.geoserver.acl.jpa.model.Rule> iterator =
                new JPAQuery<org.geoserver.acl.jpa.model.Rule>(em)
                        .from(QRule.rule)
                        .where(predicate)
                        .orderBy(new OrderSpecifier<>(Order.ASC, QRule.rule.priority))
                        .iterate();
        return iterator;
    }

    private Stream<org.geoserver.acl.jpa.model.Rule> stream(
            CloseableIterator<org.geoserver.acl.jpa.model.Rule> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                .onClose(iterator::close);
    }

    private java.util.function.Predicate<? super Rule> filterByAddress(
            Optional<RuleFilter> filter) {
        if (filter.isEmpty()) return r -> true;
        IPAddressRangeFilter ipFilter = filter.get().getSourceAddress();

        return ipFilter.toIPAddressPredicate(r -> r.getIdentifier().getAddressRange());
    }

    @Override
    @TransactionRequired
    public Rule save(Rule rule) {
        Objects.requireNonNull(rule.getId());
        org.geoserver.acl.jpa.model.Rule entity = getOrThrowIAE(rule.getId());

        long finalPriority =
                priorityResolver.resolvePriorityUpdate(entity.getPriority(), rule.getPriority());

        modelMapper.updateEntity(entity, rule);
        entity.setPriority(finalPriority);
        if (checkForDups(entity)) {
            throw new RuleIdentifierConflictException(
                    "A Rule with the same identifier already exists: "
                            + rule.getIdentifier().toShortString());
        }

        org.geoserver.acl.jpa.model.Rule saved = jparepo.save(entity);
        return modelMapper.toModel(saved);
    }

    @Override
    @TransactionRequired
    public Rule create(@NonNull Rule rule, @NonNull InsertPosition position) {
        if (null != rule.getId()) throw new IllegalArgumentException("Rule must have no id");
        if (rule.getPriority() < 0)
            throw new IllegalArgumentException(
                    "Negative priority is not allowed: " + rule.getPriority());

        final long finalPriority =
                priorityResolver.resolveFinalPriority(rule.getPriority(), position);

        org.geoserver.acl.jpa.model.Rule entity = modelMapper.toEntity(rule);
        entity.setPriority(finalPriority);
        if (checkForDups(entity)) {
            throw new RuleIdentifierConflictException(
                    "A Rule with the same identifier already exists: "
                            + rule.getIdentifier().toShortString());
        }

        org.geoserver.acl.jpa.model.Rule saved = jparepo.save(entity);

        return modelMapper.toModel(saved);
    }

    private boolean checkForDups(org.geoserver.acl.jpa.model.Rule rule) {
        if (rule.getIdentifier().getAccess() == GrantType.LIMIT) {
            return false;
        }

        RuleIdentifier identifier = rule.getIdentifier();
        List<org.geoserver.acl.jpa.model.Rule> matches = jparepo.findAllByIdentifier(identifier);
        return matches.stream().anyMatch(r -> !r.getId().equals(rule.getId()));
    }

    @Override
    @TransactionRequired
    public boolean deleteById(@NonNull String id) {
        return 1 == jparepo.deleteById(decodeId(id).longValue());
    }

    @Override
    public boolean existsById(@NonNull String id) {
        return jparepo.existsById(decodeId(id));
    }

    @Override
    @TransactionRequired
    public int shift(long priorityStart, long offset) {
        if (offset <= 0) {
            throw new IllegalArgumentException("Positive offset required");
        }
        int affectedCount = jparepo.shiftPriority(priorityStart, offset);
        return affectedCount > 0 ? affectedCount : -1;
    }

    @Override
    @TransactionRequired
    public void swap(String id1, String id2) {

        org.geoserver.acl.jpa.model.Rule rule1 = getOrThrowIAE(id1);
        org.geoserver.acl.jpa.model.Rule rule2 = getOrThrowIAE(id2);

        long p1 = rule1.getPriority();
        long p2 = rule2.getPriority();

        rule1.setPriority(p2);
        rule2.setPriority(p1);

        jparepo.saveAll(List.of(rule1, rule2));
    }

    @Override
    @TransactionRequired
    public void setAllowedStyles(@NonNull String ruleId, Set<String> styles) {

        org.geoserver.acl.jpa.model.Rule rule = getOrThrowIAE(ruleId);

        if (RuleIdentifier.ANY.equals(rule.getIdentifier().getLayer())) {
            throw new IllegalArgumentException("Rule has no layer, can't set allowed styles");
        }
        if (rule.getLayerDetails() == null || rule.getLayerDetails().isEmpty()) {
            throw new IllegalArgumentException("Rule has no details associated");
        }

        LayerDetails layerDetails = rule.getLayerDetails();
        layerDetails.getAllowedStyles().clear();
        if (styles != null && !styles.isEmpty()) {
            layerDetails.getAllowedStyles().addAll(styles);
        }
        jparepo.save(rule);
    }

    @Override
    @TransactionRequired
    public void setLimits(String ruleId, RuleLimits limits) {
        org.geoserver.acl.jpa.model.Rule rule = getOrThrowIAE(ruleId);
        if (limits != null && rule.getIdentifier().getAccess() != GrantType.LIMIT) {
            throw new IllegalArgumentException("Rule is not of LIMIT type");
        }

        rule.setRuleLimits(modelMapper.toEntity(limits));

        jparepo.save(rule);
    }

    @Override
    @TransactionRequired
    public void setLayerDetails(
            String ruleId, org.geoserver.acl.model.rules.LayerDetails detailsNew) {

        org.geoserver.acl.jpa.model.Rule rule = getOrThrowIAE(ruleId);

        if (rule.getIdentifier().getAccess() != GrantType.ALLOW && detailsNew != null)
            throw new IllegalArgumentException("Rule is not of ALLOW type");

        if (RuleIdentifier.ANY.equals(rule.getIdentifier().getLayer()) && detailsNew != null)
            throw new IllegalArgumentException("Rule does not refer to a fixed layer");

        LayerDetails details = modelMapper.toEntity(detailsNew);
        rule.setLayerDetails(details);
        jparepo.save(rule);
    }

    @Override
    @TransactionReadOnly
    public Optional<org.geoserver.acl.model.rules.LayerDetails> findLayerDetailsByRuleId(
            @NonNull String ruleId) {

        org.geoserver.acl.jpa.model.Rule jparule = getOrThrowIAE(ruleId);

        // if (RuleIdentifier.ANY.equals(jparule.getIdentifier().getLayer())) {
        // throw new IllegalArgumentException("Rule " + ruleId + " has not layer set");
        // }

        LayerDetails jpadetails = jparule.getLayerDetails();
        if (jpadetails.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(modelMapper.toModel(jpadetails));
    }

    private org.geoserver.acl.jpa.model.Rule getOrThrowIAE(@NonNull String ruleId) {
        org.geoserver.acl.jpa.model.Rule rule;
        try {
            rule = jparepo.getReferenceById(decodeId(ruleId));
            rule.getIdentifier().getLayer();
        } catch (EntityNotFoundException e) {
            throw new IllegalArgumentException("Rule " + ruleId + " does not exist");
        }
        return rule;
    }
}
