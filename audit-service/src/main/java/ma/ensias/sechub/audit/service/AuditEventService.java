package ma.ensias.sechub.audit.service;

import jakarta.persistence.criteria.Predicate;
import ma.ensias.sechub.audit.dto.AuditEventRequest;
import ma.ensias.sechub.audit.dto.AuditEventResponse;
import ma.ensias.sechub.audit.dto.PageResponse;
import ma.ensias.sechub.audit.model.AuditAction;
import ma.ensias.sechub.audit.model.AuditEvent;
import ma.ensias.sechub.audit.model.Outcome;
import ma.ensias.sechub.audit.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditEventService {

    private static final int MAX_SIZE = 100;

    private final AuditEventRepository repository;

    public AuditEventService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditEventResponse record(AuditEventRequest request) {
        AuditEvent event = new AuditEvent();
        event.setEventTime(Instant.now());
        event.setCorrelationId(request.correlationId());
        event.setActor(request.actor());
        event.setActorType(request.actorType());
        event.setAction(request.action());
        event.setResource(request.resource());
        event.setOutcome(request.outcome());
        event.setSourceService(request.sourceService());
        event.setDetails(request.details());
        return AuditEventResponse.from(repository.save(event));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(String actor, AuditAction action, Outcome outcome,
                                                   Instant from, Instant to, int page, int size) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(actor)) {
                predicates.add(cb.equal(root.get("actor"), actor));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("eventTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("eventTime"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("eventTime"), Sort.Order.desc("id")));

        Page<AuditEventResponse> result = repository.findAll(spec, pageable).map(AuditEventResponse::from);
        return PageResponse.from(result);
    }
}