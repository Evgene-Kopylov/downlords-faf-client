package com.faforever.client.preferences;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.test.ServiceTest;
import com.google.common.eventbus.EventBus;
import com.sun.jna.platform.win32.Shell32Util;
import com.sun.jna.platform.win32.ShlObj;
import org.bridj.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class PreferencesServiceTest extends ServiceTest {

  private static final Pattern GAME_LOG_PATTERN = Pattern.compile("game(_\\d*)?.log");


  private PreferencesService instance;

  @Mock
  private EventBus eventBus;
  @Mock
  private ClientProperties clientProperties;

  @BeforeEach
  public void setUp() throws Exception {
    instance = new PreferencesService(clientProperties);
  }

  @Test
  public void testGetPreferencesDirectory() throws Exception {
    assertThat(instance.getPreferencesDirectory(), notNullValue());
  }

  @Test
  public void testGetFafBinDirectory() throws Exception {
    assertThat(instance.getFafBinDirectory(), is(instance.getFafDataDirectory().resolve("bin")));
  }

  @Test
  public void testGetFafDataDirectory() throws Exception {
    if (Platform.isWindows()) {
      assertThat(instance.getFafDataDirectory(), is(Path.of(Shell32Util.getFolderPath(ShlObj.CSIDL_COMMON_APPDATA), "FAForever")));
    } else {
      assertThat(instance.getFafDataDirectory(), is(Path.of(System.getProperty("user.home")).resolve(".faforever")));
    }
  }

  @Test
  public void testGetFafReposDirectory() throws Exception {
    assertThat(instance.getPatchReposDirectory(), is(instance.getFafDataDirectory().resolve("repos")));
  }

  @Test
  public void testGetCorruptedReplaysDirectory() throws Exception {
    Path result = instance.getCorruptedReplaysDirectory();
    Path expected = instance.getReplaysDirectory().resolve("corrupt");
    assertThat(result, is(expected));
  }

  @Test
  public void testGetReplaysDirectory() throws Exception {
    assertThat(instance.getReplaysDirectory(), is(instance.getFafDataDirectory().resolve("replays")));
  }

  @Test
  public void testGetCacheDirectory() throws Exception {
    assertThat(instance.getCacheDirectory(), is(instance.getFafDataDirectory().resolve("cache")));
  }
}
