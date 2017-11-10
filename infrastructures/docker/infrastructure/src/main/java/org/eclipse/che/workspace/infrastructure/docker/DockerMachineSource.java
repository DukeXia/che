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
package org.eclipse.che.workspace.infrastructure.docker;

import org.eclipse.che.infrastructure.docker.client.DockerRegistryAuthResolver;

/**
 * Set of helper methods that identifies docker image properties
 *
 * @author Florent Benoit
 */
public class DockerMachineSource {

  /** image type support with recipe script being the name of the repository + image name */
  public static final String DOCKER_IMAGE_TYPE = "image";

  /** Optional registry (like docker-registry.company.com:5000) */
  private String registry;

  /** mandatory repository name (like codenvy/ubuntu_jdk8) */
  private String repository;

  /** optional tag of the image (like latest) */
  private String tag;

  /** optional digest of the image (like sha256@1234) */
  private String digest;

  /**
   * Build image source based on given arguments
   *
   * @param repository as for example codenvy/ubuntu_jdk8
   */
  public DockerMachineSource(String repository) {
    super();
    this.repository = repository;
  }

  /**
   * Defines optional tag attribute
   *
   * @param tag as for example latest
   * @return current instance
   */
  public DockerMachineSource withTag(String tag) {
    this.tag = tag;
    return this;
  }

  /**
   * Defines optional tag attribute
   *
   * @param tag as for example latest
   */
  public void setTag(String tag) {
    this.tag = tag;
  }

  /**
   * Defines optional registry attribute
   *
   * @param registry as for example docker-registry.company.com:5000
   */
  public void setRegistry(String registry) {
    this.registry = registry;
  }

  /**
   * Defines optional registry attribute
   *
   * @param registry as for example docker-registry.company.com:5000
   * @return current instance
   */
  public DockerMachineSource withRegistry(String registry) {
    this.registry = registry;
    return this;
  }

  /**
   * Defines optional digest attribute
   *
   * @param digest as for example sha256@1234
   */
  public void setDigest(String digest) {
    this.digest = digest;
  }

  /**
   * Defines optional digest attribute
   *
   * @param digest as for example sha256@1234
   * @return current instance
   */
  public DockerMachineSource withDigest(String digest) {
    this.digest = digest;
    return this;
  }

  /** @return mandatory repository */
  public String getRepository() {
    return this.repository;
  }

  /** @return optional tag */
  public String getTag() {
    return this.tag;
  }

  /** @return optional registry */
  public String getRegistry() {
    return this.registry;
  }

  /** @return optional digest */
  public String getDigest() {
    return this.digest;
  }

  /**
   * Returns location of this docker image, including all data that are required to reconstruct a
   * new docker machine source.
   */
  public String getLocation() {
    return getLocation(true);
  }

  /**
   * Returns full name of docker image.
   *
   * <p>It consists of registry, repository name, tag, digest. E.g.
   * docker-registry.company.com:5000/my-repository:some-tag E.g.
   * docker-registry.company.com:5000/my-repository@some-digest But in case of default registry it
   * will be omitted.
   *
   * @param includeDigest whether digest should to be included or not
   */
  public String getLocation(boolean includeDigest) {
    final StringBuilder fullRepoId = new StringBuilder();

    // optional registry is followed by /
    // should be excluded in case of default registry because of problems with swarm
    if (getRegistry() != null
        && !DockerRegistryAuthResolver.DEFAULT_REGISTRY_SYNONYMS.contains(getRegistry())) {
      fullRepoId.append(getRegistry()).append('/');
    }

    // repository
    fullRepoId.append(getRepository());

    // optional tag (: prefix)
    if (getTag() != null) {
      fullRepoId.append(':').append(getTag());
    }

    // optional digest (@ prefix)
    if (includeDigest && getDigest() != null) {
      fullRepoId.append('@').append(getDigest());
    }
    return fullRepoId.toString();
  }

  @Override
  public String toString() {
    return "DockerMachineSource{"
        + "registry='"
        + registry
        + '\''
        + ", repository='"
        + repository
        + '\''
        + ", tag='"
        + tag
        + '\''
        + ", digest='"
        + digest
        + '\''
        + '}';
  }
}
