/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.workspace.server.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.model.workspace.Warning;
import org.eclipse.che.api.core.model.workspace.config.Environment;
import org.eclipse.che.api.core.model.workspace.config.MachineConfig;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.api.installer.server.InstallerRegistry;
import org.eclipse.che.api.installer.server.exception.InstallerException;
import org.eclipse.che.api.installer.shared.model.Installer;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.ServerConfigImpl;

/**
 * Factory for Environment specific internal representation Related but not really bound to some
 * specific Infrastructure to let Infrastructure apply multiple different implementations, some of
 * which can be considered as a "native format", while others as rather "supported, adopted formats"
 *
 * <p>Expected to be bound as a MapBinder with unique String as a key, like: MapBinder<String,
 * InternalEnvironmentFactory> environmentFactories = MapBinder.newMapBinder(binder(), String.class,
 * InternalEnvironmentFactory.class);
 * environmentFactories.addBinding("uniq_name").to(SubclassOfInternalEnvironmentFactory.class);
 *
 * @author gazarenkov
 */
public abstract class InternalEnvironmentFactory {

  protected final InstallerRegistry installerRegistry;
  protected final RecipeRetriever recipeRetriever;

  public InternalEnvironmentFactory(
      InstallerRegistry installerRegistry, RecipeRetriever recipeRetriever) {
    this.installerRegistry = installerRegistry;
    this.recipeRetriever = recipeRetriever;
  }

  /**
   * validates internals of Environment and creates instance of InternalEnvironment
   *
   * @param environment the environment
   * @return InternalEnvironment
   * @throws InfrastructureException if infrastructure specific error occures
   * @throws ValidationException if validation fails
   */
  public final InternalEnvironment create(final Environment environment)
      throws InfrastructureException, ValidationException {

    Map<String, InternalMachineConfig> machines = new HashMap<>();
    List<Warning> warnings = new ArrayList<>();

    // Workaround related to written docker image id into location instead of content
    EnvironmentImpl envCopy = new EnvironmentImpl(environment);
    if ("dockerimage".equals(environment.getRecipe().getType())
        && environment.getRecipe().getLocation() != null) {
      // move image from location to content
      envCopy.getRecipe().setContent(environment.getRecipe().getLocation());
      envCopy.getRecipe().setLocation(null);
    }

    InternalRecipe recipe = recipeRetriever.getRecipe(envCopy.getRecipe());

    for (Map.Entry<String, ? extends MachineConfig> machineEntry :
        envCopy.getMachines().entrySet()) {
      MachineConfig machineConfig = machineEntry.getValue();

      List<Installer> installers = null;
      try {
        installers = installerRegistry.getOrderedInstallers(machineConfig.getInstallers());
      } catch (InstallerException e) {
        throw new InfrastructureException(e);
      }

      machines.put(
          machineEntry.getKey(),
          new InternalMachineConfig(
              installers,
              normalizeServers(machineConfig.getServers()),
              machineConfig.getEnv(),
              machineConfig.getAttributes()));
    }

    return create(machines, recipe, warnings);
  }

  /**
   * Implementation validates recipe and creates specific InternalEnvironment
   *
   * @param machines InternalMachineConfigs
   * @param recipe recipe
   * @param warnings list of warnings
   * @throws InfrastructureException if infrastructure specific error occures
   * @throws ValidationException if validation fails
   * @return InternalEnvironment
   */
  protected abstract InternalEnvironment create(
      Map<String, InternalMachineConfig> machines, InternalRecipe recipe, List<Warning> warnings)
      throws InfrastructureException, ValidationException;

  private Map<String, ServerConfig> normalizeServers(Map<String, ? extends ServerConfig> servers) {
    return servers
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getKey, e -> normalizeServer(e.getValue())));
  }

  private ServerConfig normalizeServer(ServerConfig serverConfig) {
    String port = serverConfig.getPort();
    if (port != null && !port.contains("/")) {
      port = port + "/tcp";
    }
    return new ServerConfigImpl(port, serverConfig.getProtocol(), serverConfig.getPath());
  }
}