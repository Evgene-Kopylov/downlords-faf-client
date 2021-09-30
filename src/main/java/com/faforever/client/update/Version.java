package com.faforever.client.update;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.versioning.ComparableVersion;

@Slf4j
public final class Version {
  @VisibleForTesting
  static final ComparableVersion UNSPECIFIED_VERSION = new ComparableVersion("0.0.0");
  @VisibleForTesting
  public static ComparableVersion currentVersion;

  static {
    String version = Version.class.getPackage().getImplementationVersion();
    currentVersion = version != null ? new ComparableVersion(version) : UNSPECIFIED_VERSION;

    log.info("The current application version is: {}", currentVersion);
  }

  private Version() {
    // static class
  }

  public static ComparableVersion getCurrentVersion() {
    return currentVersion;
  }

  /**
   * Compares a remote version with the current version of the application.
   *
   * @return true if the remote version is higher than the current version
   */
  public static boolean shouldUpdate(ComparableVersion version) {
    log.debug("Comparing version '{}' to remote version '{}'", currentVersion, version);

    if (currentVersion.equals(UNSPECIFIED_VERSION)) {
      log.info("Snapshot versions are not to be updated");
      return false;
    }

    if (currentVersion.compareTo(version) > -1) {
      log.info("Version '{}' is not newer than current version '{}'", version, currentVersion);
      return false;
    }
    log.info("Version '{}' is newer than current version '{}'", version, currentVersion);
    return true;
  }
}
