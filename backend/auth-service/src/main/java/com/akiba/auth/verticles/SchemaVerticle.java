package com.akiba.auth.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Runs once on startup — creates all auth schema tables and seeds
 * roles + permissions. Every statement uses IF NOT EXISTS so this
 * is safe to re-run after a restart without duplicating data.
 */
public class SchemaVerticle extends AbstractVerticle {

  private Pool pgPool;

  @Override
  public void start(Promise<Void> startPromise) {
    pgPool = createPgPool();

    createSchema()
      .compose(v -> createBaseTablesInParallel())
      .compose(v -> createRolePermissionsTable())
      .compose(v -> createUserTablesInParallel())
      .compose(v -> seedRoles())
      .compose(v -> seedPermissions())
      .compose(v -> seedRolePermissions())
      .onSuccess(v -> {
        System.out.println("[SchemaVerticle] ✅ All tables created and seeded");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[SchemaVerticle] ❌ Schema setup failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  // ─── Pool Setup ────────────────────────────────────────────────────────────

  private Pool createPgPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost(System.getenv().getOrDefault("DB_HOST", "localhost"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
      .setDatabase(System.getenv().getOrDefault("DB_NAME", "akiba_db"))
      .setUser(System.getenv().getOrDefault("DB_USER", "akiba"))
      .setPassword(System.getenv().getOrDefault("DB_PASS", "akiba_secret"));

    return PgBuilder.pool()
      .with(new PoolOptions().setMaxSize(5))
      .connectingTo(connectOptions)
      .using(vertx)
      .build();
  }

  // ─── Step 1: Create Schema ─────────────────────────────────────────────────

  private Future<Void> createSchema() {
    return pgPool.query("CREATE SCHEMA IF NOT EXISTS auth")
      .execute()
      .mapEmpty();
  }

  // ─── Step 2: Base tables (parallel — no foreign key deps yet) ─────────────

  private Future<Void> createBaseTablesInParallel() {
    Future<Void> roles = pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.roles (
        id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        name       VARCHAR     UNIQUE NOT NULL,
        created_at TIMESTAMP   DEFAULT NOW()
      )
      """).execute().mapEmpty();

    Future<Void> permissions = pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.permissions (
        id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        name        VARCHAR     UNIQUE NOT NULL,
        description TEXT,
        created_at  TIMESTAMP   DEFAULT NOW()
      )
      """).execute().mapEmpty();

    return Future.all(roles, permissions).mapEmpty();
  }

  // ─── Step 3: role_permissions depends on both roles + permissions ──────────

  private Future<Void> createRolePermissionsTable() {
    return pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.role_permissions (
        role_id       UUID REFERENCES auth.roles(id)       ON DELETE CASCADE,
        permission_id UUID REFERENCES auth.permissions(id) ON DELETE CASCADE,
        PRIMARY KEY (role_id, permission_id)
      )
      """).execute().mapEmpty();
  }

  // ─── Step 4: User tables (parallel — all depend on roles) ─────────────────

  private Future<Void> createUserTablesInParallel() {
    Future<Void> users = pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.users (
        id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
        full_name           VARCHAR      NOT NULL,
        email               VARCHAR      UNIQUE NOT NULL,
        phone               VARCHAR(15)  UNIQUE NOT NULL,
        password_hash       VARCHAR      NOT NULL,
        role_id             UUID         REFERENCES auth.roles(id),
        status              VARCHAR(20)  DEFAULT 'PENDING_VERIFICATION',
        profile_picture_url TEXT,
        created_at          TIMESTAMP    DEFAULT NOW(),
        updated_at          TIMESTAMP    DEFAULT NOW()
      )
      """).execute().mapEmpty();

    Future<Void> otps = pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.otps (
        id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id    UUID        REFERENCES auth.users(id) ON DELETE CASCADE,
        otp_code   VARCHAR(6)  NOT NULL,
        type       VARCHAR(20) NOT NULL,
        expires_at TIMESTAMP   NOT NULL,
        used       BOOLEAN     DEFAULT false,
        created_at TIMESTAMP   DEFAULT NOW()
      )
      """).execute().mapEmpty();

    Future<Void> sessions = pgPool.query("""
      CREATE TABLE IF NOT EXISTS auth.sessions (
        id            UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
        user_id       UUID      REFERENCES auth.users(id) ON DELETE CASCADE,
        refresh_token TEXT      UNIQUE NOT NULL,
        expires_at    TIMESTAMP NOT NULL,
        revoked       BOOLEAN   DEFAULT false,
        created_at    TIMESTAMP DEFAULT NOW()
      )
      """).execute().mapEmpty();

    return Future.all(users, otps, sessions).mapEmpty();
  }

  // ─── Step 5: Seed Roles ────────────────────────────────────────────────────

  private Future<Void> seedRoles() {
    return pgPool.query("""
      INSERT INTO auth.roles (name) VALUES
        ('ROLE_USER'),
        ('ROLE_ADMIN')
      ON CONFLICT (name) DO NOTHING
      """).execute().mapEmpty();
  }

  // ─── Step 6: Seed Permissions ──────────────────────────────────────────────

  private Future<Void> seedPermissions() {
    return pgPool.query("""
      INSERT INTO auth.permissions (name, description) VALUES
        ('transactions:read',    'View own transactions'),
        ('transactions:write',   'Create and categorize transactions'),
        ('statements:upload',    'Upload M-Pesa and bank statements'),
        ('budgets:read',         'View own budgets'),
        ('budgets:write',        'Create and update budgets'),
        ('savings:read',         'View own savings goals'),
        ('savings:write',        'Create and update savings goals'),
        ('payments:send',        'Send M-Pesa payments'),
        ('ai:query',             'Query AI financial assistant'),
        ('profile:read',         'View own profile'),
        ('profile:write',        'Update own profile'),
        ('notifications:read',   'View own notifications'),
        ('admin:users:read',     'Admin — view all users'),
        ('admin:users:write',    'Admin — deactivate or modify users'),
        ('admin:reports:read',   'Admin — view system reports')
      ON CONFLICT (name) DO NOTHING
      """).execute().mapEmpty();
  }

  // ─── Step 7: Map permissions to roles ─────────────────────────────────────

  private Future<Void> seedRolePermissions() {
    // ROLE_USER gets all non-admin permissions
    Future<Void> userPerms = pgPool.query("""
      INSERT INTO auth.role_permissions (role_id, permission_id)
        SELECT r.id, p.id
        FROM auth.roles r, auth.permissions p
        WHERE r.name = 'ROLE_USER'
          AND p.name NOT LIKE 'admin:%'
      ON CONFLICT DO NOTHING
      """).execute().mapEmpty();

    // ROLE_ADMIN gets all permissions
    Future<Void> adminPerms = pgPool.query("""
      INSERT INTO auth.role_permissions (role_id, permission_id)
        SELECT r.id, p.id
        FROM auth.roles r, auth.permissions p
        WHERE r.name = 'ROLE_ADMIN'
      ON CONFLICT DO NOTHING
      """).execute().mapEmpty();

    return Future.all(userPerms, adminPerms).mapEmpty();
  }

  @Override
  public void stop() {
    if (pgPool != null) pgPool.close();
  }
}
