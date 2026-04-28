package com.akiba.transaction.verticles;

import com.akiba.transaction.consumers.TransactionSaveConsumer;
import com.akiba.transaction.handlers.*;
import com.akiba.transaction.middleware.JwtMiddleware;
import com.akiba.transaction.repositories.TransactionRepository;
import com.akiba.transaction.services.AnomalyDetectionService;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends VerticleBase {

    @Override
    public Future<?> start() {
        Pool           pool = buildPool();
        RabbitMQClient mq   = buildRabbitMQClient();
        JWTAuth        jwt  = buildJwtAuth();

        TransactionRepository   txRepo         = new TransactionRepository(pool);
        AnomalyDetectionService anomalyService = new AnomalyDetectionService(pool);
        TransactionSaveConsumer consumer       = new TransactionSaveConsumer(mq, txRepo, anomalyService);

        return vertx.deployVerticle(new SchemaVerticle(pool))
            .compose(id -> mq.start())
            .compose(v  -> consumer.start())
            .compose(v  -> startHttpServer(txRepo, jwt))
            .onSuccess(v -> System.out.println("[TransactionService] Started on port " + servicePort()));
    }

    private Future<Void> startHttpServer(TransactionRepository txRepo, JWTAuth jwt) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // Health check is public — registered BEFORE the JWT middleware
        router.get("/transactions/health")
            .handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\",\"service\":\"transaction-service\"}"));

        // All routes below this point require a valid JWT.
        // router.route() with no path matches every route from here down.
        router.route().handler(new JwtMiddleware(jwt));

        router.get("/transactions")
            .handler(new GetTransactionsHandler(txRepo)::handle);
        router.get("/transactions/summary")
            .handler(new GetSummaryHandler(txRepo)::handle);
        router.get("/transactions/top-merchants")
            .handler(new GetTopMerchantsHandler(txRepo)::handle);
        router.get("/transactions/:id")
            .handler(new GetTransactionByIdHandler(txRepo)::handle);
        router.post("/transactions")
            .handler(new CreateTransactionHandler(txRepo)::handle);
        router.put("/transactions/:id/category")
            .handler(new UpdateCategoryHandler(txRepo)::handle);

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(servicePort())
            .mapEmpty();
    }

    private JWTAuth buildJwtAuth() {
        // Must match the exact same secret the Auth Service used to sign tokens
        return JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer(System.getenv("JWT_SECRET"))));
    }

    private Pool buildPool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(System.getenv("DB_HOST"))
            .setPort(Integer.parseInt(System.getenv("DB_PORT")))
            .setDatabase(System.getenv("DB_NAME"))
            .setUser(System.getenv("DB_USER"))
            .setPassword(System.getenv("DB_PASS"));
        return PgBuilder.pool()
            .with(new PoolOptions().setMaxSize(10))
            .connectingTo(connectOptions)
            .using(vertx)
            .build();
    }

    private RabbitMQClient buildRabbitMQClient() {
        return RabbitMQClient.create(vertx,
            new RabbitMQOptions().setHost(System.getenv("RABBITMQ_HOST")));
    }

    private int servicePort() {
        String port = System.getenv("SERVICE_PORT");
        return port != null ? Integer.parseInt(port) : 8082;
    }
}
