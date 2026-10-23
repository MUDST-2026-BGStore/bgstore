package com.chanakanlabs.theparlor;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Explicit documentation tool; generating files is not a regression test. */
public final class ModuleDocumentation {
  private ModuleDocumentation() {}

  public static void main(String[] args) {
    new Documenter(ApplicationModules.of(TheparlorApiApplication.class))
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml()
        .writeModuleCanvases();
  }
}
