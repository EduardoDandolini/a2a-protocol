package br.com.talk.a2a.spring;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monta o servidor A2A como beans Spring.
 *
 * <p>O SDK Java oficial do A2A so publica implementacoes de referencia baseadas em Quarkus/CDI
 * ({@code a2a-java-sdk-reference-jsonrpc} usa rotas Vert.x, {@code @ApplicationScoped} e
 * {@code io.quarkus.security}). Como este projeto e Spring Boot, montamos aqui os mesmos
 * componentes do nucleo ({@code a2a-java-sdk-server-common} +
 * {@code a2a-java-sdk-transport-jsonrpc}), que sao independentes de framework, e expomos o
 * endpoint JSON-RPC com um {@link A2AJsonRpcController}.
 *
 * <p>Todas as classes do SDK usadas aqui tem construtores publicos "normais" alem dos
 * construtores anotados com {@code @Inject}, entao instancia-las manualmente e suportado.
 * O {@code @PostConstruct}/{@code @PreDestroy} do {@link MainEventBusProcessor} continua
 * sendo executado porque o Spring processa anotacoes {@code jakarta.annotation} tambem em
 * objetos devolvidos por metodos {@code @Bean}.
 *
 * <p>Cada aplicacao (Demo A e os tres agentes da Demo B) fornece seu proprio
 * {@link AgentCard} e {@link AgentExecutor} como beans; opcionalmente tambem um
 * {@link TaskAuthorizationProvider} (Demo B) e um {@link ServerCallContextFactory}.
 */
@Configuration
public class A2AServerConfiguration {

    /**
     * Restringe o autowiring do Spring as anotacoes DO SPRING.
     *
     * <p>Por padrao, quando {@code jakarta.inject} esta no classpath, o Spring tambem trata
     * {@code @Inject} como ponto de injecao. As classes do servidor A2A carregam
     * {@code @Inject} em CAMPOS destinados ao CDI do Quarkus (por exemplo
     * {@code DefaultRequestHandler.configProvider} e os {@code Instance<...>}), e o Spring
     * tentaria satisfaze-los — falhando na subida com "required a bean of type
     * A2AConfigProvider".
     *
     * <p>Esses campos so fazem sentido dentro de um container CDI. Aqui os objetos do SDK sao
     * construidos explicitamente, com tudo o que precisam, pelos metodos {@code @Bean} abaixo.
     * Entao dizemos ao Spring para autowirar apenas {@code @Autowired} e {@code @Value}.
     *
     * <p>Precisa ser {@code static} para ser registrado antes dos demais beans.
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor
            springOnlyAutowiringProcessor() {
        return beanFactory -> beanFactory.getBean(
                        org.springframework.context.annotation.AnnotationConfigUtils
                                .AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME,
                        org.springframework.beans.factory.annotation
                                .AutowiredAnnotationBeanPostProcessor.class)
                .setAutowiredAnnotationTypes(java.util.Set.of(
                        org.springframework.beans.factory.annotation.Autowired.class,
                        org.springframework.beans.factory.annotation.Value.class));
    }

    /**
     * Executor usado pelo {@link DefaultRequestHandler} para rodar o {@link AgentExecutor}
     * fora da thread da requisicao HTTP.
     */
    @Bean(destroyMethod = "close")
    public A2AExecutors a2aExecutors() {
        return new A2AExecutors();
    }

    @Bean
    public MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    /**
     * Task store em memoria.
     *
     * <p>Simplificacao de demo: em producao isto seria um task store em JPA (ou outro
     * armazenamento duravel), exatamente como o {@code CompensationLog} da Demo B.
     * A interface {@link TaskStore} do SDK permite trocar a implementacao sem tocar no agente.
     */
    @Bean
    public InMemoryTaskStore a2aTaskStore(ObjectProvider<TaskAuthorizationProvider> authorizationProvider) {
        return new InMemoryTaskStore(authorizationProvider.getIfAvailable());
    }

    @Bean
    public QueueManager a2aQueueManager(InMemoryTaskStore taskStore, MainEventBus mainEventBus) {
        return new InMemoryQueueManager(taskStore, mainEventBus);
    }

    @Bean
    public PushNotificationSender a2aPushNotificationSender() {
        // Push notifications nao fazem parte do roteiro da palestra.
        return new NoOpPushNotificationSender();
    }

    @Bean
    public MainEventBusProcessor a2aMainEventBusProcessor(MainEventBus mainEventBus,
                                                          TaskStore taskStore,
                                                          PushNotificationSender pushNotificationSender,
                                                          QueueManager queueManager) {
        return new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
    }

    /**
     * O {@link RequestHandler} e o ponto em que a task e aceita.
     *
     * <p>Lacuna 2 da palestra ("autorizacao e tardia") mora exatamente aqui: quando existe um
     * {@link TaskAuthorizationProvider} no contexto, o SDK chama
     * {@code checkCreate}/{@code checkWrite} ANTES de a task ir para {@code submitted} e antes
     * de o {@link AgentExecutor} — e portanto qualquer {@code @Tool} — ser invocado.
     *
     * <p>Na Demo A nao existe provider, e {@code authorizationRequired} e desligado
     * explicitamente: qualquer um que alcance a porta 8080 cria tasks.
     */
    private static RequestHandler buildRequestHandler(AgentExecutor agentExecutor,
                                                      TaskStore taskStore,
                                                      QueueManager queueManager,
                                                      MainEventBusProcessor mainEventBusProcessor,
                                                      A2AExecutors executors,
                                                      TaskAuthorizationProvider provider) {
        return DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .mainEventBusProcessor(mainEventBusProcessor)
                .executor(executors.agentExecutorPool())
                .eventConsumerExecutor(executors.eventConsumerPool())
                .authorizationProvider(provider)
                // Sem provider o SDK e fail-closed. A Demo A quer justamente o contrario:
                // "aceita todo mundo". A Demo B fornece provider e mantem authorizationRequired.
                .authorizationRequired(provider != null)
                .pushNotificationsEnabled(false)
                .build();
    }

    /**
     * O {@link JSONRPCHandler}, com o {@link RequestHandler} construido aqui dentro.
     *
     * <p>O {@code DefaultRequestHandler} nao e exposto como bean de proposito: ele tem um
     * {@code @PostConstruct initConfig()} pensado para o CDI do Quarkus, que le um
     * {@code A2AConfigProvider} e SOBRESCREVERIA tudo o que o builder acabou de configurar
     * (inclusive o {@code authorizationProvider} e o {@code authorizationRequired}, que sao
     * o coracao da Demo B). Fora do container de beans do Spring, esse metodo nunca roda,
     * e o caminho do builder — que o proprio SDK documenta — vale.
     */
    @Bean
    public JSONRPCHandler a2aJsonRpcHandler(AgentCard agentCard,
                                            AgentExecutor agentExecutor,
                                            TaskStore taskStore,
                                            QueueManager queueManager,
                                            MainEventBusProcessor mainEventBusProcessor,
                                            A2AExecutors executors,
                                            ObjectProvider<TaskAuthorizationProvider> authorizationProvider) {
        RequestHandler requestHandler = buildRequestHandler(agentExecutor, taskStore, queueManager,
                mainEventBusProcessor, executors, authorizationProvider.getIfAvailable());
        // Construtor publico "sem CDI" do SDK: (AgentCard, RequestHandler, Executor).
        return new JSONRPCHandler(agentCard, requestHandler, executors.agentExecutorPool());
    }


    /** Pools dedicados, fechados junto com o contexto Spring. */
    public static final class A2AExecutors implements AutoCloseable {
        private final java.util.concurrent.ExecutorService agentExecutorPool =
                Executors.newVirtualThreadPerTaskExecutor();
        private final java.util.concurrent.ExecutorService eventConsumerPool =
                Executors.newVirtualThreadPerTaskExecutor();

        public Executor agentExecutorPool() {
            return agentExecutorPool;
        }

        public Executor eventConsumerPool() {
            return eventConsumerPool;
        }

        @Override
        public void close() {
            agentExecutorPool.shutdown();
            eventConsumerPool.shutdown();
        }
    }
}
