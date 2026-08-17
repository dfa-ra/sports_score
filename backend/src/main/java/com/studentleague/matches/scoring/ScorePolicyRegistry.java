package com.studentleague.matches.scoring;

import com.studentleague.common.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScorePolicyRegistry {

    private final Map<String, ScorePolicy> policies = new HashMap<>();
    private final ScorePolicy fallback;

    public ScorePolicyRegistry(List<ScorePolicy> scorePolicies) {
        this.fallback = new PointBasedScorePolicy("DEFAULT");
        for (ScorePolicy policy : scorePolicies) {
            policies.put(policy.sportCode().toUpperCase(), policy);
        }
        policies.putIfAbsent("BASKETBALL", new PointBasedScorePolicy("BASKETBALL"));
        policies.putIfAbsent("VOLLEYBALL", new PointBasedScorePolicy("VOLLEYBALL"));
        policies.putIfAbsent("HOCKEY", new PointBasedScorePolicy("HOCKEY"));
    }

    public ScorePolicy forSportCode(String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Sport code is required for scoring");
        }
        return policies.getOrDefault(code.toUpperCase(), fallback);
    }
}
