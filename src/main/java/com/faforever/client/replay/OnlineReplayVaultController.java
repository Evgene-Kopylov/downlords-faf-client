package com.faforever.client.replay;

import com.faforever.client.domain.FeaturedModBean;
import com.faforever.client.domain.ReplayBean;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.OpenOnlineReplayVaultEvent;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.main.event.ShowUserReplaysEvent;
import com.faforever.client.mod.ModService;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.query.CategoryFilterController;
import com.faforever.client.query.SearchablePropertyMappings;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.vault.VaultEntityController;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import com.faforever.commons.api.dto.Game;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OnlineReplayVaultController extends VaultEntityController<ReplayBean> {

  private static final int TOP_ELEMENT_COUNT = 6;

  private final ModService modService;
  private final LeaderboardService leaderboardService;
  private final ReplayService replayService;

  private int playerId;
  private ReplayDetailController replayDetailController;

  public OnlineReplayVaultController(ModService modService, LeaderboardService leaderboardService, ReplayService replayService, UiService uiService, NotificationService notificationService, I18n i18n, PreferencesService preferencesService, ReportingService reportingService) {
    super(uiService, notificationService, i18n, preferencesService, reportingService);
    this.leaderboardService = leaderboardService;
    this.replayService = replayService;
    this.modService = modService;
  }

  @Override
  public void initialize() {
    super.initialize();
    uploadButton.setVisible(false);
  }

  @Override
  protected void onDisplayDetails(ReplayBean replay) {
    JavaFxUtil.assertApplicationThread();
    replayDetailController.setReplay(replay);
    replayDetailController.getRoot().setVisible(true);
    replayDetailController.getRoot().requestFocus();
  }

  protected void setSupplier(SearchConfig searchConfig) {
    switch (searchType) {
      case SEARCH -> currentSupplier = replayService.findByQueryWithPageCount(searchConfig, pageSize, pagination.getCurrentPageIndex() + 1);
      case OWN -> currentSupplier = replayService.getOwnReplaysWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case NEWEST -> currentSupplier = replayService.getNewestReplaysWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case HIGHEST_RATED -> currentSupplier = replayService.getHighestRatedReplaysWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case PLAYER -> currentSupplier = replayService.getReplaysForPlayerWithPageCount(playerId, pageSize, pagination.getCurrentPageIndex() + 1);
    }
  }

  protected Node getEntityCard(ReplayBean replay) {
    ReplayCardController controller = uiService.loadFxml("theme/vault/replay/replay_card.fxml");
    controller.setReplay(replay);
    controller.setOnOpenDetailListener(this::onDisplayDetails);
    return controller.getRoot();
  }

  @Override
  protected List<ShowRoomCategory> getShowRoomCategories() {
    return Arrays.asList(
        new ShowRoomCategory(() -> replayService.getOwnReplaysWithPageCount(TOP_ELEMENT_COUNT, 1), SearchType.OWN, "vault.replays.ownReplays"),
        new ShowRoomCategory(() -> replayService.getNewestReplaysWithPageCount(TOP_ELEMENT_COUNT, 1), SearchType.NEWEST, "vault.replays.newest"),
        new ShowRoomCategory(() -> replayService.getHighestRatedReplaysWithPageCount(TOP_ELEMENT_COUNT, 1), SearchType.HIGHEST_RATED, "vault.replays.highestRated")
    );
  }

  public void onUploadButtonClicked() {
    // do nothing
  }

  @Override
  protected void onManageVaultButtonClicked() {
    // do nothing
  }

  @Override
  protected Node getDetailView() {
    replayDetailController = uiService.loadFxml("theme/vault/replay/replay_detail.fxml");
    return replayDetailController.getRoot();
  }

  protected void initSearchController() {
    searchController.setRootType(Game.class);
    searchController.setSearchableProperties(SearchablePropertyMappings.GAME_PROPERTY_MAPPING);
    searchController.setSortConfig(preferencesService.getPreferences().getVault().onlineReplaySortConfigProperty());
    searchController.setOnlyShowLastYearCheckBoxVisible(true);
    searchController.setVaultRoot(vaultRoot);
    searchController.setSavedQueries(preferencesService.getPreferences().getVault().getSavedReplayQueries());

    searchController.addTextFilter("playerStats.player.login", i18n.get("game.player.username"), true);
    searchController.addTextFilter("mapVersion.map.displayName", i18n.get("game.map.displayName"), false);
    searchController.addTextFilter("mapVersion.map.author.login", i18n.get("game.map.author"), false);
    searchController.addTextFilter("name", i18n.get("game.title"), false);
    searchController.addTextFilter("id", i18n.get("game.id"), true);

    CategoryFilterController featuredModFilterController = searchController.addCategoryFilter("featuredMod.displayName",
        i18n.get("featuredMod.displayName"), List.of());

    modService.getFeaturedMods().thenAccept(featuredMods ->
        JavaFxUtil.runLater(() ->
            featuredModFilterController.setItems(featuredMods.stream().map(FeaturedModBean::getDisplayName)
                .collect(Collectors.toList()))));

    CategoryFilterController leaderboardFilterController = searchController.addCategoryFilter("playerStats.ratingChanges.leaderboard.id",
        i18n.get("leaderboard.displayName"), Map.of());

    leaderboardService.getLeaderboards().thenAccept(leaderboards -> {
      Map<String, String> leaderboardItems = new LinkedHashMap<>();
      leaderboards.forEach(leaderboard -> leaderboardItems.put(i18n.getOrDefault(leaderboard.getTechnicalName(), leaderboard.getNameKey()), String.valueOf(leaderboard.getId())));
      JavaFxUtil.runLater(() ->
          leaderboardFilterController.setItems(leaderboardItems));
    });

    //TODO: Use rating rather than estimated mean with an assumed deviation of 300 when that is available
    searchController.addRangeFilter("playerStats.ratingChanges.meanBefore", i18n.get("game.rating"),
        0, 3000, 100, (value) -> value + 300);

    searchController.addDateRangeFilter("endTime", i18n.get("game.date"), 1);
    searchController.addToggleFilter("validity", i18n.get("game.onlyRanked"), "VALID");

  }

  @Override
  protected Class<? extends NavigateEvent> getDefaultNavigateEvent() {
    return OpenOnlineReplayVaultEvent.class;
  }

  @Override
  protected void handleSpecialNavigateEvent(NavigateEvent navigateEvent) {
    if (navigateEvent instanceof ShowReplayEvent) {
      onShowReplayEvent((ShowReplayEvent) navigateEvent);
    } else if (navigateEvent instanceof ShowUserReplaysEvent) {
      onShowUserReplaysEvent((ShowUserReplaysEvent) navigateEvent);
    } else {
      log.warn("No such NavigateEvent for this Controller: {}", navigateEvent.getClass());
    }
  }

  private void onShowReplayEvent(ShowReplayEvent event) {
    int replayId = event.getReplayId();
    if (state.get() == State.UNINITIALIZED) {
      ChangeListener<State> stateChangeListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
          if (newValue != State.SHOWROOM) {
            return;
          }
          showReplayWithID(replayId);
          state.removeListener(this);
        }
      };
      state.addListener(stateChangeListener);
      //We have to wait for the Show Room to load otherwise it will not be loaded and it looks strange
      loadShowRoom();
    } else {
      showReplayWithID(replayId);
    }
  }

  private void showReplayWithID(int replayId) {
    replayService.findById(replayId).thenAccept(replay -> {
      if (replay.isPresent()) {
        JavaFxUtil.runLater(() -> onDisplayDetails(replay.get()));
      } else {
        notificationService.addNotification(new ImmediateNotification(i18n.get("replay.notFoundTitle"), i18n.get("replay.replayNotFoundText", replayId), Severity.WARN));
      }
    });
  }

  private void onShowUserReplaysEvent(ShowUserReplaysEvent event) {
    enterSearchingState();
    searchType = SearchType.PLAYER;
    playerId = event.getPlayerId();
    displayFromSupplier(() -> replayService.getReplaysForPlayerWithPageCount(playerId, pageSize, 1), true);
  }
}
