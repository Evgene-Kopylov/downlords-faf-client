package com.faforever.client.discord;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.domain.GameBean;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.commons.lobby.GameStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Service
public class DiscordRichPresenceService implements DisposableBean {
  private static final String HOSTING = "Hosting";
  private static final String WAITING = "Waiting";
  private static final String PLAYING = "Playing";
  /**
   * It is suggested as per the library's GitHub page to look for callbacks every 5 seconds.
   * @see <a href="https://github.com/Vatuu/discord-rpc">Projects GitHubPage</a>
   */
  private static final int INITIAL_DELAY_FOR_CALLBACK_MILLIS = 5000;
  private static final int PERIOD_FOR_CALLBACK_MILLIS = 5000;

  private final ClientProperties clientProperties;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;
  private final Timer timer;
  private final ObjectMapper objectMapper;


  public DiscordRichPresenceService(PlayerService playerService, DiscordEventHandler discordEventHandler,
                                    ClientProperties clientProperties, PreferencesService preferencesService,
                                    ObjectMapper objectMapper) {
    this.playerService = playerService;
    this.clientProperties = clientProperties;
    this.preferencesService = preferencesService;
    this.objectMapper = objectMapper;
    this.timer = new Timer("Discord RPC", true);
    String applicationId = clientProperties.getDiscord().getApplicationId();
    if (applicationId == null) {
      return;
    }
    try {
      DiscordRPC.discordInitialize(applicationId, discordEventHandler, true);
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          DiscordRPC.discordRunCallbacks();
        }
      }, INITIAL_DELAY_FOR_CALLBACK_MILLIS, PERIOD_FOR_CALLBACK_MILLIS);
    } catch (Throwable t) {
      //TODO: report to bugsnap
      log.error("Error in discord init", t);
    }
  }

  public void updatePlayedGameTo(GameBean game) {
    log.debug("Updating discord rich presence game info");
    String applicationId = clientProperties.getDiscord().getApplicationId();
    if (applicationId == null) {
      return;
    }
    try {

      if (game == null || game.getStatus() == GameStatus.CLOSED || game.getTeams() == null) {
        DiscordRPC.discordClearPresence();
        return;
      }

      DiscordRichPresence.Builder discordRichPresence = new DiscordRichPresence.Builder(getDiscordState(game));
      discordRichPresence.setDetails(MessageFormat.format("{0} | {1}", game.getFeaturedMod(), game.getTitle()));
      discordRichPresence.setParty(String.valueOf(game.getId()), game.getNumPlayers(), game.getMaxPlayers());
      discordRichPresence.setSmallImage(clientProperties.getDiscord().getSmallImageKey(), "");
      discordRichPresence.setBigImage(clientProperties.getDiscord().getBigImageKey(), "");
      String joinSecret = null;
      String spectateSecret = null;
      if (game.getStatus() == GameStatus.OPEN && !preferencesService.getPreferences().isDisallowJoinsViaDiscord()) {
        joinSecret = objectMapper.writeValueAsString(new DiscordJoinSecret(game.getId()));
      }

      if (game.getStatus() == GameStatus.PLAYING && game.getStartTime() != null && game.getStartTime().isAfter(OffsetDateTime.now().plusSeconds(clientProperties.getReplay().getWatchDelaySeconds()))) {
        spectateSecret = objectMapper.writeValueAsString(new DiscordSpectateSecret(game.getId()));
      }

      discordRichPresence.setSecrets(joinSecret, spectateSecret);

      if (game.getStartTime() != null) {
        discordRichPresence.setStartTimestamps(game.getStartTime().toEpochSecond());
      } else if (game.getStatus() == GameStatus.PLAYING) {
        discordRichPresence.setStartTimestamps(Instant.now().toEpochMilli());
      }
      DiscordRichPresence update = discordRichPresence.build();
      DiscordRPC.discordUpdatePresence(update);
    } catch (Throwable throwable) {
      //TODO: report to bugsnap
      log.error("Error reporting game status to discord", throwable);
    }
  }

  private String getDiscordState(GameBean game) {
    //I want no internationalisation in here as it should always be English
    switch (game.getStatus()) {
      case OPEN:
        boolean isHost = game.getHost().equals(playerService.getCurrentPlayer().getUsername());
        return isHost ? HOSTING : WAITING;
      default:
        return PLAYING;
    }
  }

  @Override
  public void destroy() {
    DiscordRPC.discordShutdown();
    timer.cancel();
  }

  public void clearGameInfo() {
    try {
      DiscordRPC.discordClearPresence();
    } catch (Throwable t) {
      log.error("Error cleaning game info for discord", t);
    }
  }
}
