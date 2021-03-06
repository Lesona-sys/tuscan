package pro.tuscan.domain.match;

import pro.tuscan.adapter.api.response.MatchFullDetailsResponse;
import pro.tuscan.adapter.api.response.SimpleMatchesResponse;
import pro.tuscan.client.match.FaceitMatchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);

    private final FaceitMatchClient faceitMatchClient;
    private final MatchRepository matchRepository;

    public MatchService(FaceitMatchClient faceitMatchClient, MatchRepository matchRepository) {
        this.faceitMatchClient = faceitMatchClient;
        this.matchRepository = matchRepository;
    }

    public Mono<Match> getMatch(String matchId) {
        return matchRepository.findById(matchId)
                .switchIfEmpty(Mono.error(new MatchNotFoundException("Match not found")));
    }

    public Mono<SimpleMatchesResponse> getMatches(String playerId, Integer offset) {
        return faceitMatchClient.getMatches(playerId, offset)
                .onErrorResume(e -> faceitMatchClient.fallbackToV1Matches(playerId));
    }

    public Mono<MatchFullDetailsResponse> getMatchByPlayer(String matchId, String playerId) {
        return matchRepository.findById(matchId)
                .map(match -> DomainMatchMapper.Companion.toResponse(match, playerId))
                .switchIfEmpty(Mono.defer(() -> getMatchFromFaceitApi(matchId, playerId)));
    }

    public Mono<Match> updateMatchDemoDetailsStatus(String matchId, DemoStatus demoStatus) {
        return matchRepository.findById(matchId)
                .map(match -> DomainMatchMapper.Companion.updateWithDemoStatus(match, demoStatus))
                .flatMap(matchRepository::save);
    }

    private Mono<MatchFullDetailsResponse> getMatchFromFaceitApi(String matchId, String playerId) {
        logger.info("Match {} not found in database, fallback to Faceit API", matchId);
        return faceitMatchClient.getMatchDetails(matchId, playerId)
                .doOnNext(r -> matchRepository.save(DomainMatchMapper.Companion.fromResponse(r))
                        .doOnSuccess(match -> logger.info("Saved new match {}", match.getMatchId()))
                        .subscribe());
    }
}
