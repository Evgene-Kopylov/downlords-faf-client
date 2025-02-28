package com.faforever.client.preferences.ui;

import com.faforever.client.builders.PreferencesBuilder;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.GameService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.LanguageChannel;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TimeInfo;
import com.faforever.client.settings.LanguageItemController;
import com.faforever.client.test.UITest;
import com.faforever.client.theme.Theme;
import com.faforever.client.theme.UiService;
import com.faforever.client.update.ClientUpdateService;
import com.faforever.client.user.UserService;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.ReadOnlySetWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.collections.FXCollections;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SettingsControllerTest extends UITest {
  private static final Theme FIRST_THEME = new Theme("First", "none", 1, "1");
  private static final Theme SECOND_THEME = new Theme("Second", "none", 1, "1");

  private SettingsController instance;
  @Mock
  private UserService userService;
  @Mock
  private PreferencesService preferenceService;
  @Mock
  private UiService uiService;
  @Mock
  private EventBus eventBus;
  @Mock
  private I18n i18n;
  @Mock
  private NotificationService notificationService;
  @Mock
  private GameService gameService;
  @Mock
  private PlatformService platformService;
  @Mock
  private ClientProperties clientProperties;
  @Mock
  private ClientUpdateService clientUpdateService;

  private Preferences preferences;
  private SimpleSetProperty<Locale> availableLanguages;

  @BeforeEach
  public void setUp() throws Exception {
    preferences = PreferencesBuilder.create().defaultValues().get();
    when(preferenceService.getPreferences()).thenReturn(preferences);
    when(uiService.currentThemeProperty()).thenReturn(new SimpleObjectProperty<>());
    when(uiService.getCurrentTheme())
        .thenReturn(FIRST_THEME);
    when(uiService.getAvailableThemes())
        .thenReturn(Arrays.asList(
            FIRST_THEME,
            SECOND_THEME
        ));
    when(gameService.isGamePrefsPatchedToAllowMultiInstances()).thenReturn(CompletableFuture.completedFuture(true));

    availableLanguages = new SimpleSetProperty<>(FXCollections.observableSet());
    when(i18n.getAvailableLanguages()).thenReturn(new ReadOnlySetWrapper<>(availableLanguages));

    instance = new SettingsController(userService, preferenceService, uiService, i18n, eventBus, notificationService, platformService, clientProperties, clientUpdateService, gameService);
    loadFxml("theme/settings/settings.fxml", param -> instance);
  }

  @Test
  public void testThemesDisplayed() {
    assertThat(instance.themeComboBox.getSelectionModel().getSelectedItem(), is(FIRST_THEME));
    assertThat(instance.themeComboBox.getItems(), hasItem(FIRST_THEME));
    assertThat(instance.themeComboBox.getItems(), hasItem(SECOND_THEME));
  }

  @Test
  public void testSelectingSecondThemeCausesReloadAndRestartPrompt() {
    when(uiService.doesThemeNeedRestart(SECOND_THEME)).thenReturn(true);
    instance.themeComboBox.getSelectionModel().select(SECOND_THEME);
    WaitForAsyncUtils.waitForFxEvents();
    verify(uiService).setTheme(SECOND_THEME);
    verify(notificationService).addNotification(any(PersistentNotification.class));
  }

  @Test
  public void testSelectingDefaultThemeDoesNotCausesRestartPrompt() {
    when(uiService.doesThemeNeedRestart(SECOND_THEME)).thenReturn(false);
    instance.themeComboBox.getSelectionModel().select(SECOND_THEME);
    WaitForAsyncUtils.waitForFxEvents();
    verify(notificationService, never()).addNotification(any(PersistentNotification.class));
    verify(uiService).setTheme(SECOND_THEME);
  }

  @Test
  public void testSearchForBetaUpdateIfOptionIsTurnedOn() {
    instance.prereleaseToggle.setSelected(true);
    verify(clientUpdateService).checkForUpdateInBackground();
    instance.prereleaseToggle.setSelected(false);
    verifyNoMoreInteractions(clientUpdateService);
  }

  @Test
  public void testOnLanguageSelected() {
    preferences.getLocalization().setLanguage(Locale.US);
    instance.onLanguageSelected(Locale.GERMAN);

    verify(notificationService).addNotification(any(PersistentNotification.class));
  }

  @Test
  public void testOnLanguageSelectedThatIsAlreadySet() {
    preferences.getLocalization().setLanguage(Locale.GERMAN);
    instance.onLanguageSelected(Locale.GERMAN);

    verify(notificationService, never()).addNotification(any(PersistentNotification.class));
  }

  @Test
  public void testOnTimeSelected() {
    instance.timeComboBox.setValue(TimeInfo.AUTO);

    instance.onTimeFormatSelected();

    assertThat(preferences.getChat().getTimeFormat(), is(instance.timeComboBox.getValue()));
  }

  @Test
  public void testAvailableLanguagesChange() {
    LanguageItemController languageItemController = mock(LanguageItemController.class);
    when(languageItemController.getRoot()).thenReturn(new Pane());
    when(uiService.loadFxml("theme/settings/language_item.fxml")).thenReturn(languageItemController);

    availableLanguages.clear();
    availableLanguages.addAll(Set.of(Locale.FRENCH));

    verify(languageItemController).setLocale(Locale.FRENCH);
    verify(languageItemController).setOnSelectedListener(any());
    verify(uiService).loadFxml("theme/settings/language_item.fxml");
  }

  @Test
  public void testOnAddChannelButtonPressed() {
    preferences.getChat().getAutoJoinChannels().clear();
    instance.channelTextField.setText("#newbie");
    instance.onAddAutoChannel();
    List<String> expected = Collections.singletonList("#newbie");
    assertThat(preferences.getChat().getAutoJoinChannels(), is(expected));
  }

  @Test
  public void testLanguageChannels() {
    Map<Locale, LanguageChannel> languagesToChannels = ChatPrefs.LOCALE_LANGUAGES_TO_CHANNELS;
    Entry<Locale, LanguageChannel> firstEntry = languagesToChannels.entrySet().iterator().next();
    Locale.setDefault(firstEntry.getKey());

    List<String> expected = Collections.singletonList(firstEntry.getValue().getChannelName());
    preferences.getChat().getAutoJoinChannels().setAll(expected);

    assertThat(instance.autoChannelListView.getItems(), is(expected));
  }

  @Test
  public void testOnAddMirrorButtonPressed() {
    preferences.getMirror().getMirrorURLs().clear();
    instance.mirrorURITextField.setText("https://faf-mirror.example.com/");
    instance.onAddMirrorURL();
    List<URI> expected = Collections.singletonList(URI.create("https://faf-mirror.example.com/"));
    assertThat(preferences.getMirror().getMirrorURLs(), is(expected));
    assertThat(instance.mirrorURITextField.getText(), is(""));
  }

  @Test
  public void testOnAddMirrorButtonPressedDuplicate() {
    preferences.getMirror().getMirrorURLs().clear();
    instance.mirrorURITextField.setText("https://faf-mirror.example.com");
    instance.onAddMirrorURL();
    assertThat(instance.mirrorURITextField.getText(), is(""));
    instance.mirrorURITextField.setText("https://faf-mirror.example.com");
    instance.onAddMirrorURL();
    assertThat(instance.mirrorURITextField.getText(), is("https://faf-mirror.example.com"));
    instance.onAddMirrorURL();
    List<URI> expected = Collections.singletonList(URI.create("https://faf-mirror.example.com/"));
    assertThat(preferences.getMirror().getMirrorURLs(), is(expected));
    assertThat(instance.mirrorURITextField.getText(), is("https://faf-mirror.example.com"));
  }

  @Test
  public void testOnAddMirrorButtonPressedEmpty() {
    preferences.getMirror().getMirrorURLs().clear();
    instance.mirrorURITextField.setText("");
    instance.onAddMirrorURL();
    assertThat(preferences.getMirror().getMirrorURLs(), is(Collections.emptyList()));
    assertThat(instance.mirrorURITextField.getText(), is(""));
  }

  @Test
  public void testOnAddMirrorButtonPressedBadUrl() {
    preferences.getMirror().getMirrorURLs().clear();
    instance.mirrorURITextField.setText("faf-mirror.example.com");
    instance.onAddMirrorURL();
    assertThat(preferences.getMirror().getMirrorURLs(), is(Collections.emptyList()));
    assertThat(instance.mirrorURITextField.getText(), is("faf-mirror.example.com"));
    instance.mirrorURITextField.setText("spam://faf-mirror.example.com");
    instance.onAddMirrorURL();
    assertThat(preferences.getMirror().getMirrorURLs(), is(Collections.emptyList()));
    assertThat(instance.mirrorURITextField.getText(), is("spam://faf-mirror.example.com"));
  }
}
