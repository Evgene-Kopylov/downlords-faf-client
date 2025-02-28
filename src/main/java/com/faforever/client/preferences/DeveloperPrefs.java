package com.faforever.client.preferences;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal=true, level= AccessLevel.PRIVATE)
public class DeveloperPrefs {
  StringProperty gameRepositoryUrl = new SimpleStringProperty("https://github.com/FAForever/fa.git");

  public String getGameRepositoryUrl() {
    return gameRepositoryUrl.get();
  }

  public void setGameRepositoryUrl(String gameRepositoryUrl) {
    this.gameRepositoryUrl.set(gameRepositoryUrl);
  }

  public StringProperty gameRepositoryUrlProperty() {
    return gameRepositoryUrl;
  }
}
