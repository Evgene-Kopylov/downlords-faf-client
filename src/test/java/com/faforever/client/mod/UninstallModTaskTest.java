package com.faforever.client.mod;

import com.faforever.client.builders.ModBeanBuilder;
import com.faforever.client.builders.ModVersionBeanBuilder;
import com.faforever.client.domain.ModVersionBean;
import com.faforever.client.test.ServiceTest;
import com.faforever.commons.io.ByteCopier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class UninstallModTaskTest extends ServiceTest {

  private static final ClassPathResource BLACKOPS_SUPPORT_MOD_INFO = new ClassPathResource("/mods/blackops_support_mod_info.lua");
  private static final ClassPathResource ECO_MANAGER_MOD_INFO = new ClassPathResource("/mods/eco_manager_mod_info.lua");

  @TempDir
  public Path modsDirectory;

  @Mock
  private ModService modService;

  private UninstallModTask instance;

  @BeforeEach
  public void setUp() throws Exception {
    instance = new UninstallModTask(modService);
  }

  @Test
  public void testCallWithoutModThrowsException() throws Exception {
    assertEquals("modVersion has not been set", assertThrows(NullPointerException.class, () -> instance.call()).getMessage());
  }

  @Test
  public void testCall() throws Exception {
    copyMod("blackOpsSupport", BLACKOPS_SUPPORT_MOD_INFO);
    copyMod("ecoManager", ECO_MANAGER_MOD_INFO);

    ModVersionBean modVersion = ModVersionBeanBuilder.create().defaultValues().mod(ModBeanBuilder.create().defaultValues().get()).uid("b2cde810-15d0-4bfa-af66-ec2d6ecd561b").get();

    Path ecoManagerPath = Files.createDirectories(modsDirectory.resolve("ecoManager"));
    when(modService.getPathForMod(modVersion)).thenReturn(ecoManagerPath);

    instance.setModVersion(modVersion);
    instance.call();

    assertThat(Files.exists(modsDirectory.resolve("blackOpsSupport")), is(true));
    assertThat(Files.exists(ecoManagerPath), is(false));
  }

  private void copyMod(String directoryName, ClassPathResource classPathResource) throws IOException {
    Path targetDir = Files.createDirectories(modsDirectory.resolve(directoryName));

    try (InputStream inputStream = classPathResource.getInputStream();
         OutputStream outputStream = Files.newOutputStream(targetDir.resolve("mod_info.lua"))) {
      ByteCopier.from(inputStream)
          .to(outputStream)
          .copy();
    }
  }
}
