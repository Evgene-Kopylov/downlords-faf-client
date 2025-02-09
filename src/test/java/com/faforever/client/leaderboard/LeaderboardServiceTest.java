package com.faforever.client.leaderboard;


import com.faforever.client.api.FafApiAccessor;
import com.faforever.client.builders.LeaderboardBeanBuilder;
import com.faforever.client.builders.LeaderboardEntryBeanBuilder;
import com.faforever.client.builders.LeagueBeanBuilder;
import com.faforever.client.builders.LeagueEntryBeanBuilder;
import com.faforever.client.builders.LeagueSeasonBeanBuilder;
import com.faforever.client.builders.PlayerBeanBuilder;
import com.faforever.client.builders.SubdivisionBeanBuilder;
import com.faforever.client.domain.LeaderboardBean;
import com.faforever.client.domain.LeaderboardEntryBean;
import com.faforever.client.domain.LeagueBean;
import com.faforever.client.domain.LeagueEntryBean;
import com.faforever.client.domain.LeagueSeasonBean;
import com.faforever.client.domain.PlayerBean;
import com.faforever.client.domain.SubdivisionBean;
import com.faforever.client.mapstruct.CycleAvoidingMappingContext;
import com.faforever.client.mapstruct.LeaderboardMapper;
import com.faforever.client.mapstruct.MapperSetup;
import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.AssetService;
import com.faforever.client.test.ElideMatchers;
import com.faforever.client.test.ServiceTest;
import com.faforever.commons.api.dto.LeagueSeasonDivisionSubdivision;
import com.faforever.commons.api.dto.LeagueSeasonScore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.faforever.commons.api.elide.ElideNavigator.qBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LeaderboardServiceTest extends ServiceTest {

  @Mock
  private AssetService assetService;
  @Mock
  private FafApiAccessor fafApiAccessor;
  @Mock
  private PlayerService playerService;

  private LeaderboardService instance;

  private LeaderboardBean leaderboard;
  private final LeaderboardMapper leaderboardMapper = Mappers.getMapper(LeaderboardMapper.class);
  private PlayerBean player;

  @BeforeEach
  public void setUp() throws Exception {
    MapperSetup.injectMappers(leaderboardMapper);
    player = PlayerBeanBuilder.create().defaultValues().id(1).username("junit").get();
    leaderboard = LeaderboardBeanBuilder.create().defaultValues().get();

    instance = new LeaderboardService(assetService, fafApiAccessor, leaderboardMapper, playerService);
  }

  @Test
  public void testGetLeaderboards() {
    LeaderboardBean leaderboardBean = LeaderboardBeanBuilder.create().defaultValues().get();

    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(leaderboardMapper.map(leaderboardBean, new CycleAvoidingMappingContext())));

    List<LeaderboardBean> results = instance.getLeaderboards().toCompletableFuture().join();

    verify(fafApiAccessor).getMany(any());
    assertThat(results, hasSize(1));
    assertThat(results.get(0), is(leaderboardBean));
  }

  @Test
  public void testGetEntriesForPlayer() {
    LeaderboardEntryBean leaderboardEntryBean = LeaderboardEntryBeanBuilder.create().defaultValues().get();
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(leaderboardEntryBean, new CycleAvoidingMappingContext())));

    List<LeaderboardEntryBean> result = instance.getEntriesForPlayer(player).toCompletableFuture().join();
    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasFilter(qBuilder().intNum("player.id").eq(player.getId()))));
    Assertions.assertEquals(List.of(leaderboardEntryBean), result);
  }

  @Test
  public void testGetLeagues() {
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.empty());

    instance.getLeagues().toCompletableFuture().join();

    verify(fafApiAccessor).getMany(any());
  }

  @Test
  public void testGetLatestSeason() {
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.empty());
    LeagueBean league = LeagueBeanBuilder.create().defaultValues().get();

    instance.getLatestSeason(league).toCompletableFuture().join();

    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasSort("startDate", false)));
    verify(fafApiAccessor).getMany(argThat(ElideMatchers.filterPresent()));
  }

  @Test
  public void testGetPlayerNumberInHigherDivisions() {
    SubdivisionBean subdivisionBean1 = SubdivisionBeanBuilder.create().defaultValues().index(1).get();
    SubdivisionBean subdivisionBean2 = SubdivisionBeanBuilder.create().defaultValues().index(2).get();
    SubdivisionBean subdivisionBean3 = SubdivisionBeanBuilder.create().defaultValues().index(3).get();
    LeagueEntryBean leagueEntryBean1 = LeagueEntryBeanBuilder.create().defaultValues().score(8).subdivision(subdivisionBean2).get();
    LeagueEntryBean leagueEntryBean2 = LeagueEntryBeanBuilder.create().defaultValues().get();
    when(fafApiAccessor.getManyWithPageCount(argThat(ElideMatchers.hasDtoClass(LeagueSeasonScore.class)))).thenReturn(Mono.zip(
        Mono.just(List.of(
            leaderboardMapper.map(leagueEntryBean1, new CycleAvoidingMappingContext()),
            leaderboardMapper.map(leagueEntryBean2, new CycleAvoidingMappingContext())
        )), Mono.just(2)));
    when(fafApiAccessor.getMany(argThat(ElideMatchers.hasDtoClass(LeagueSeasonDivisionSubdivision.class)))).thenReturn(Flux.just(
        leaderboardMapper.map(subdivisionBean1, new CycleAvoidingMappingContext()),
        leaderboardMapper.map(subdivisionBean2, new CycleAvoidingMappingContext()),
        leaderboardMapper.map(subdivisionBean3, new CycleAvoidingMappingContext())));

    int result = instance.getPlayerNumberInHigherDivisions(subdivisionBean2).toCompletableFuture().join();
    Assertions.assertEquals(2, result);
  }

  @Test
  public void testGetTotalPlayers() {
    when(fafApiAccessor.getManyWithPageCount(any())).thenReturn(Mono.zip(Mono.just(List.of()), Mono.just(0)));
    LeagueSeasonBean season = LeagueSeasonBeanBuilder.create().defaultValues().get();

    int result = instance.getTotalPlayers(season).toCompletableFuture().join();

    verify(fafApiAccessor).getManyWithPageCount(argThat(ElideMatchers.filterPresent()));
    verify(fafApiAccessor).getManyWithPageCount(argThat(ElideMatchers.hasPageSize(1)));
    Assertions.assertEquals(0, result);
  }

  @Test
  public void testGetSizeOfDivision() {
    SubdivisionBean subdivisionBean = SubdivisionBeanBuilder.create().defaultValues().get();
    LeagueEntryBean leagueEntryBean = LeagueEntryBeanBuilder.create().defaultValues().subdivision(subdivisionBean).get();
    when(fafApiAccessor.getManyWithPageCount(any())).thenReturn(Mono.zip(
        Mono.just(List.of(leaderboardMapper.map(leagueEntryBean, new CycleAvoidingMappingContext()))), Mono.just(1)));

    int result = instance.getSizeOfDivision(subdivisionBean).toCompletableFuture().join();

    verify(fafApiAccessor).getManyWithPageCount(argThat(ElideMatchers.filterPresent()));
    verify(fafApiAccessor).getManyWithPageCount(argThat(ElideMatchers.hasPageSize(1)));
    Assertions.assertEquals(1, result);
  }

  @Test
  public void testGetLeagueEntryForPlayer() {
    LeagueEntryBean leagueEntryBean = LeagueEntryBeanBuilder.create().defaultValues().get();
    LeagueSeasonBean season = LeagueSeasonBeanBuilder.create().defaultValues().id(2).get();
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(leagueEntryBean, new CycleAvoidingMappingContext())));

    LeagueEntryBean result = instance.getLeagueEntryForPlayer(player, season).toCompletableFuture().join();
    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasFilter(qBuilder().intNum("loginId").eq(player.getId())
        .and().intNum("leagueSeason.id").eq(2))));
    Assertions.assertEquals(leagueEntryBean, result);
  }

  @Test
  public void testGetHighestLeagueEntryForPlayer() {
    SubdivisionBean subdivisionBean1 = SubdivisionBeanBuilder.create().defaultValues().index(2).get();
    SubdivisionBean subdivisionBean2 = SubdivisionBeanBuilder.create().defaultValues().index(3).get();
    LeagueEntryBean leagueEntryBean1 = LeagueEntryBeanBuilder.create().defaultValues().subdivision(subdivisionBean1).get();
    LeagueEntryBean leagueEntryBean2 = LeagueEntryBeanBuilder.create().defaultValues().subdivision(subdivisionBean2).get();
    LeagueEntryBean leagueEntryBean3 = LeagueEntryBeanBuilder.create().defaultValues().subdivision(null).get();

    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(leagueEntryBean1, new CycleAvoidingMappingContext()),
        leaderboardMapper.map(leagueEntryBean2, new CycleAvoidingMappingContext()),
        leaderboardMapper.map(leagueEntryBean3, new CycleAvoidingMappingContext())));

    Optional<LeagueEntryBean> result = instance.getHighestLeagueEntryForPlayer(player).toCompletableFuture().join();

    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasFilter(qBuilder().intNum("loginId").eq(player.getId()))));
    Assertions.assertEquals(leagueEntryBean2, result.orElse(null));
  }

  @Test
  public void testGetHighestLeagueEntryForPlayerNoSubdivision() {
    LeagueEntryBean leagueEntryBean = LeagueEntryBeanBuilder.create().defaultValues().subdivision(null).get();

    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(leagueEntryBean, new CycleAvoidingMappingContext())));

    Optional<LeagueEntryBean> result = instance.getHighestLeagueEntryForPlayer(player).toCompletableFuture().join();

    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasFilter(qBuilder().intNum("loginId").eq(player.getId()))));
    Assertions.assertTrue(result.isEmpty());
  }

  @Test
  public void testGetLeagueEntries() {
    SubdivisionBean subdivisionBean = SubdivisionBeanBuilder.create().defaultValues().get();
    LeagueEntryBean leagueEntryBean = LeagueEntryBeanBuilder.create().defaultValues().subdivision(subdivisionBean).get();
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(leagueEntryBean, new CycleAvoidingMappingContext())));
    when(playerService.getPlayersByIds(List.of(1))).thenReturn(
        CompletableFuture.completedFuture(List.of(PlayerBeanBuilder.create().id(1).username("junit").get())));

    List<LeagueEntryBean> result = instance.getEntries(subdivisionBean).toCompletableFuture().join();
    Assertions.assertEquals(List.of(leagueEntryBean), result);
  }

  @Test
  public void testGetLeagueEntriesEmpty() {
    SubdivisionBean subdivisionBean = SubdivisionBeanBuilder.create().defaultValues().get();
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.empty());

    List<LeagueEntryBean> result = instance.getEntries(subdivisionBean).toCompletableFuture().join();
    Assertions.assertEquals(List.of(), result);
  }


  @Test
  public void testGetAllSubdivisions() {
    LeagueSeasonBean season = LeagueSeasonBeanBuilder.create().defaultValues().id(0).get();
    SubdivisionBean subdivisionBean = SubdivisionBeanBuilder.create().defaultValues().get();
    when(fafApiAccessor.getMany(any())).thenReturn(Flux.just(
        leaderboardMapper.map(subdivisionBean, new CycleAvoidingMappingContext())));

    List<SubdivisionBean> result = instance.getAllSubdivisions(season).toCompletableFuture().join();
    verify(fafApiAccessor).getMany(argThat(ElideMatchers.hasFilter(
        qBuilder().string("leagueSeasonDivision.leagueSeason.id").eq("0"))));
    Assertions.assertEquals(List.of(subdivisionBean), result);
  }

  @Test
  public void testLoadDivisionImage() {
    SubdivisionBean subdivisionBean = SubdivisionBeanBuilder.create().defaultValues().get();
    instance.loadDivisionImage(subdivisionBean.getImageUrl());
    verify(assetService).loadAndCacheImage(subdivisionBean.getImageUrl(), Path.of("divisions"), null);
  }
}
