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
import static org.eclipse.persistence.config.PersistenceUnitProperties.JDBC_DRIVER;
import static org.eclipse.persistence.config.PersistenceUnitProperties.JDBC_PASSWORD;
import static org.eclipse.persistence.config.PersistenceUnitProperties.JDBC_URL;
import static org.eclipse.persistence.config.PersistenceUnitProperties.JDBC_USER;
import static org.eclipse.persistence.config.PersistenceUnitProperties.TRANSACTION_TYPE;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.jpa.JpaPersistModule;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.spi.PersistenceUnitTransactionType;
import org.eclipse.che.account.spi.AccountImpl;
import org.eclipse.che.api.machine.server.jpa.JpaRecipeDao;
import org.eclipse.che.api.machine.server.recipe.RecipeImpl;
import org.eclipse.che.api.machine.server.spi.RecipeDao;
import org.eclipse.che.api.user.server.jpa.JpaUserDao;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.user.server.spi.UserDao;
import org.eclipse.che.api.workspace.server.jpa.JpaStackDao;
import org.eclipse.che.api.workspace.server.jpa.JpaWorkspaceDao;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackImpl;
import org.eclipse.che.api.workspace.server.spi.StackDao;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.commons.test.tck.JpaCleaner;
import org.eclipse.che.commons.test.tck.TckModule;
import org.eclipse.che.commons.test.tck.TckResourcesCleaner;
import org.eclipse.che.commons.test.tck.repository.JpaTckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.eclipse.che.commons.test.tck.repository.TckRepositoryException;
import org.eclipse.che.core.db.DBInitializer;
import org.eclipse.che.core.db.schema.SchemaInitializer;
import org.eclipse.che.core.db.schema.impl.flyway.FlywaySchemaInitializer;
import org.eclipse.che.multiuser.api.permission.server.AbstractPermissionsDomain;
import org.eclipse.che.multiuser.api.permission.server.SystemDomain;
import org.eclipse.che.multiuser.api.permission.server.jpa.JpaSystemPermissionsDao;
import org.eclipse.che.multiuser.api.permission.server.model.impl.SystemPermissionsImpl;
import org.eclipse.che.multiuser.api.permission.server.spi.PermissionsDao;
import org.eclipse.che.multiuser.api.permission.server.spi.tck.SystemPermissionsDaoTest;
import org.eclipse.che.multiuser.organization.api.permissions.OrganizationDomain;
import org.eclipse.che.multiuser.organization.spi.MemberDao;
import org.eclipse.che.multiuser.organization.spi.OrganizationDao;
import org.eclipse.che.multiuser.organization.spi.OrganizationDistributedResourcesDao;
import org.eclipse.che.multiuser.organization.spi.impl.MemberImpl;
import org.eclipse.che.multiuser.organization.spi.impl.OrganizationDistributedResourcesImpl;
import org.eclipse.che.multiuser.organization.spi.impl.OrganizationImpl;
import org.eclipse.che.multiuser.organization.spi.jpa.JpaMemberDao;
import org.eclipse.che.multiuser.organization.spi.jpa.JpaOrganizationDao;
import org.eclipse.che.multiuser.organization.spi.jpa.JpaOrganizationDistributedResourcesDao;
import org.eclipse.che.multiuser.permission.machine.jpa.JpaRecipePermissionsDao;
import org.eclipse.che.multiuser.permission.machine.recipe.RecipePermissionsImpl;
import org.eclipse.che.multiuser.permission.machine.spi.tck.RecipePermissionsDaoTest;
import org.eclipse.che.multiuser.permission.workspace.server.model.impl.WorkerImpl;
import org.eclipse.che.multiuser.permission.workspace.server.spi.WorkerDao;
import org.eclipse.che.multiuser.permission.workspace.server.spi.jpa.JpaStackPermissionsDao;
import org.eclipse.che.multiuser.permission.workspace.server.spi.jpa.JpaWorkerDao;
import org.eclipse.che.multiuser.permission.workspace.server.spi.tck.StackPermissionsDaoTest;
import org.eclipse.che.multiuser.permission.workspace.server.spi.tck.WorkerDaoTest;
import org.eclipse.che.multiuser.permission.workspace.server.stack.StackPermissionsImpl;
import org.eclipse.che.multiuser.resource.spi.FreeResourcesLimitDao;
import org.eclipse.che.multiuser.resource.spi.impl.FreeResourcesLimitImpl;
import org.eclipse.che.multiuser.resource.spi.jpa.JpaFreeResourcesLimitDao;
import org.eclipse.che.security.PasswordEncryptor;
import org.eclipse.che.security.SHA512PasswordEncryptor;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module for testing JPA DAO.
 *
 * @author Mihail Kuznyetsov
 */
public class PostgresqlTckModule extends TckModule {

  private static final Logger LOG = LoggerFactory.getLogger(PostgresqlTckModule.class);

  @Override
  protected void configure() {
    final Map<String, String> properties = new HashMap<>();
    properties.put(TRANSACTION_TYPE, PersistenceUnitTransactionType.RESOURCE_LOCAL.name());

    final String dbUrl = System.getProperty("jdbc.url");
    final String dbUser = System.getProperty("jdbc.user");
    final String dbPassword = System.getProperty("jdbc.password");

    waitConnectionIsEstablished(dbUrl, dbUser, dbPassword);

    properties.put(JDBC_URL, dbUrl);
    properties.put(JDBC_USER, dbUser);
    properties.put(JDBC_PASSWORD, dbPassword);
    properties.put(JDBC_DRIVER, System.getProperty("jdbc.driver"));

    JpaPersistModule main = new JpaPersistModule("main");
    main.properties(properties);
    install(main);
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUser(dbUser);
    dataSource.setPassword(dbPassword);
    dataSource.setUrl(dbUrl);
    bind(SchemaInitializer.class).toInstance(new FlywaySchemaInitializer(dataSource, "che-schema"));
    bind(DBInitializer.class).asEagerSingleton();
    bind(TckResourcesCleaner.class).to(JpaCleaner.class);

    //repositories
    //api-account
    bind(new TypeLiteral<TckRepository<AccountImpl>>() {})
        .toInstance(new JpaTckRepository<>(AccountImpl.class));
    //api-user
    bind(new TypeLiteral<TckRepository<UserImpl>>() {}).to(UserJpaTckRepository.class);

    //api-workspace
    bind(new TypeLiteral<TckRepository<WorkspaceImpl>>() {})
        .toInstance(new JpaTckRepository<>(WorkspaceImpl.class));
    bind(new TypeLiteral<TckRepository<StackImpl>>() {})
        .toInstance(new JpaTckRepository<>(StackImpl.class));
    bind(new TypeLiteral<TckRepository<WorkerImpl>>() {})
        .toInstance(new JpaTckRepository<>(WorkerImpl.class));

    //api-machine
    bind(new TypeLiteral<TckRepository<RecipeImpl>>() {})
        .toInstance(new JpaTckRepository<>(RecipeImpl.class));
    //api permission
    bind(new TypeLiteral<TckRepository<RecipePermissionsImpl>>() {})
        .toInstance(new JpaTckRepository<>(RecipePermissionsImpl.class));
    bind(new TypeLiteral<TckRepository<StackPermissionsImpl>>() {})
        .toInstance(new JpaTckRepository<>(StackPermissionsImpl.class));
    bind(new TypeLiteral<TckRepository<SystemPermissionsImpl>>() {})
        .toInstance(new JpaTckRepository<>(SystemPermissionsImpl.class));

    bind(new TypeLiteral<PermissionsDao<StackPermissionsImpl>>() {})
        .to(JpaStackPermissionsDao.class);
    bind(new TypeLiteral<PermissionsDao<RecipePermissionsImpl>>() {})
        .to(JpaRecipePermissionsDao.class);
    bind(new TypeLiteral<PermissionsDao<SystemPermissionsImpl>>() {})
        .to(JpaSystemPermissionsDao.class);

    //permissions domains
    bind(new TypeLiteral<AbstractPermissionsDomain<RecipePermissionsImpl>>() {})
        .to(RecipePermissionsDaoTest.TestDomain.class);
    bind(new TypeLiteral<AbstractPermissionsDomain<StackPermissionsImpl>>() {})
        .to(StackPermissionsDaoTest.TestDomain.class);
    bind(new TypeLiteral<AbstractPermissionsDomain<WorkerImpl>>() {})
        .to(WorkerDaoTest.TestDomain.class);
    bind(new TypeLiteral<AbstractPermissionsDomain<SystemPermissionsImpl>>() {})
        .to(SystemPermissionsDaoTest.TestDomain.class);

    //api-organization
    bind(new TypeLiteral<TckRepository<OrganizationImpl>>() {})
        .to(JpaOrganizationImplTckRepository.class);
    bind(new TypeLiteral<TckRepository<MemberImpl>>() {})
        .toInstance(new JpaTckRepository<>(MemberImpl.class));
    bind(new TypeLiteral<TckRepository<OrganizationDistributedResourcesImpl>>() {})
        .toInstance(new JpaTckRepository<>(OrganizationDistributedResourcesImpl.class));

    //api-resource
    bind(new TypeLiteral<TckRepository<FreeResourcesLimitImpl>>() {})
        .toInstance(new JpaTckRepository<>(FreeResourcesLimitImpl.class));

    //dao
    //api-user
    bind(UserDao.class).to(JpaUserDao.class);

    //api-workspace
    bind(WorkspaceDao.class).to(JpaWorkspaceDao.class);
    bind(StackDao.class).to(JpaStackDao.class);
    bind(WorkerDao.class).to(JpaWorkerDao.class);
    //api-machine
    bind(RecipeDao.class).to(JpaRecipeDao.class);
    //api-organization
    bind(OrganizationDao.class).to(JpaOrganizationDao.class);
    bind(MemberDao.class).to(JpaMemberDao.class);
    bind(OrganizationDistributedResourcesDao.class)
        .to(JpaOrganizationDistributedResourcesDao.class);
    bind(new TypeLiteral<PermissionsDao<MemberImpl>>() {}).to(JpaMemberDao.class);
    bind(new TypeLiteral<AbstractPermissionsDomain<MemberImpl>>() {}).to(OrganizationDomain.class);
    //api-resource
    bind(FreeResourcesLimitDao.class).to(JpaFreeResourcesLimitDao.class);

    // SHA-512 ecnryptor is faster than PBKDF2 so it is better for testing
    bind(PasswordEncryptor.class).to(SHA512PasswordEncryptor.class).in(Singleton.class);

    //Creates empty multibinder to avoid error during container starting
    Multibinder.newSetBinder(
        binder(), String.class, Names.named(SystemDomain.SYSTEM_DOMAIN_ACTIONS));
  }

  private static void waitConnectionIsEstablished(String dbUrl, String dbUser, String dbPassword) {
    boolean isAvailable = false;
    for (int i = 0; i < 20 && !isAvailable; i++) {
      try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
        isAvailable = true;
      } catch (SQLException x) {
        LOG.warn(
            "An attempt to connect to the database failed with an error: {}",
            x.getLocalizedMessage());
        try {
          TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException interruptedX) {
          throw new RuntimeException(interruptedX.getLocalizedMessage(), interruptedX);
        }
      }
    }
    if (!isAvailable) {
      throw new IllegalStateException("Couldn't initialize connection with a database");
    }
  }

  @Transactional
  public static class UserJpaTckRepository implements TckRepository<UserImpl> {

    @Inject private Provider<EntityManager> managerProvider;

    @Inject private PasswordEncryptor encryptor;

    @Override
    public void createAll(Collection<? extends UserImpl> entities) throws TckRepositoryException {
      final EntityManager manager = managerProvider.get();
      entities
          .stream()
          .map(
              user ->
                  new UserImpl(
                      user.getId(),
                      user.getEmail(),
                      user.getName(),
                      user.getPassword() != null ? encryptor.encrypt(user.getPassword()) : null,
                      user.getAliases()))
          .forEach(manager::persist);
    }

    @Override
    public void removeAll() throws TckRepositoryException {
      final EntityManager manager = managerProvider.get();
      manager
          .createQuery("SELECT users FROM Usr users", UserImpl.class)
          .getResultList()
          .forEach(manager::remove);
    }
  }

  /**
   * Organizations require to have own repository because it is important to delete organization in
   * reverse order that they were stored. It allows to resolve problems with removing
   * suborganization before parent organization removing.
   *
   * @author Sergii Leschenko
   */
  public static class JpaOrganizationImplTckRepository extends JpaTckRepository<OrganizationImpl> {
    @Inject protected Provider<EntityManager> managerProvider;

    @Inject protected UnitOfWork uow;

    private final List<OrganizationImpl> createdOrganizations = new ArrayList<>();

    public JpaOrganizationImplTckRepository() {
      super(OrganizationImpl.class);
    }

    @Override
    public void createAll(Collection<? extends OrganizationImpl> entities)
        throws TckRepositoryException {
      super.createAll(entities);
      //It's important to save organization to remove them in the reverse order
      createdOrganizations.addAll(entities);
    }

    @Override
    public void removeAll() throws TckRepositoryException {
      uow.begin();
      final EntityManager manager = managerProvider.get();
      try {
        manager.getTransaction().begin();

        for (int i = createdOrganizations.size() - 1; i > -1; i--) {
          // The query 'DELETE FROM ....' won't be correct as it will ignore orphanRemoval
          // and may also ignore some configuration options, while EntityManager#remove won't
          try {
            final OrganizationImpl organizationToRemove =
                manager
                    .createQuery(
                        "SELECT o FROM Organization o " + "WHERE o.id = :id",
                        OrganizationImpl.class)
                    .setParameter("id", createdOrganizations.get(i).getId())
                    .getSingleResult();
            manager.remove(organizationToRemove);
          } catch (NoResultException ignored) {
            //it is already removed
          }
        }
        createdOrganizations.clear();

        manager.getTransaction().commit();
      } catch (RuntimeException x) {
        if (manager.getTransaction().isActive()) {
          manager.getTransaction().rollback();
        }
        throw new TckRepositoryException(x.getLocalizedMessage(), x);
      } finally {
        uow.end();
      }

      //remove all objects that was created in tests
      super.removeAll();
    }
  }
}