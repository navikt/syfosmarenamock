package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.arenaSykemelding.ArenaSykmelding
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.arena.createArenaSykmelding
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.rules.RuleMetadata
import no.nav.syfo.rules.ValidationRuleChain
import no.nav.syfo.util.arenaSykmeldingMarshaller
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.readConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringWriter
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import javax.xml.bind.Marshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmarenamock")

fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    connectionFactory(config).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

        try {
            val listeners = (1..config.applicationThreads).map {
                launch {

                    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                    val arenaQueue = session.createQueue(config.arenaQueue)
                    val arenaProducer = session.createProducer(arenaQueue)

                    val consumerProperties = readConsumerConfig(config, credentials, valueDeserializer = StringDeserializer::class)
                    val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                    // TODO after srvsyfosmarena can cosune all the topics kafkaconsumer.subscribe(listOf(config.kafkaSm2013AutomaticPapirmottakTopic, config.kafkaSm2013AutomaticDigitalHandlingTopic, config.kafkaSm2013manuellPapirmottakTopic, config.kafkaSm2013manuelDigitalManuellTopic))
                    kafkaconsumer.subscribe(listOf(config.kafkaSm2013AutomaticDigitalHandlingTopic))

                    blockingApplicationLogic(applicationState, kafkaconsumer, arenaProducer, session)
                }
        }.toList()

        runBlocking {
            Runtime.getRuntime().addShutdownHook(Thread {
                applicationServer.stop(10, 10, TimeUnit.SECONDS)
            })

            applicationState.initialized = true
            listeners.forEach { it.join() }
        }
        } finally {
        applicationState.running = false
        }
    }
}

suspend fun blockingApplicationLogic(applicationState: ApplicationState, kafkaconsumer: KafkaConsumer<String, String>, arenaProducer: MessageProducer, session: Session) {
        while (applicationState.running) {
            var logValues = arrayOf(
                    StructuredArguments.keyValue("smId", "missing"),
                    StructuredArguments.keyValue("organizationNumber", "missing"),
                    StructuredArguments.keyValue("msgId", "missing")
            )

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
                "{}"
            }

            kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                logValues = arrayOf(
                        StructuredArguments.keyValue("smId", receivedSykmelding.navLogId),
                        StructuredArguments.keyValue("organizationNumber", receivedSykmelding.legekontorOrgNr),
                        StructuredArguments.keyValue("msgId", receivedSykmelding.msgId)
                )

                log.info("Received a SM2013, going to Arena rules, $logKeys", *logValues)

                val validationRuleResults = ValidationRuleChain.values().executeFlow(receivedSykmelding.sykmelding, RuleMetadata(
                        receivedDate = receivedSykmelding.mottattDato,
                        signatureDate = receivedSykmelding.signaturDato,
                        rulesetVersion = receivedSykmelding.rulesetVersion
                ))

                val results = listOf(validationRuleResults).flatten()

                log.info("Finish with rules")

                // TODO map rules to arena hendelse
                val arenaSykmelding = createArenaSykmelding(receivedSykmelding, results)
                sendArenaSykmelding(arenaProducer, session, arenaSykmelding, logKeys, logValues)
            }
        }
        delay(100)
    }

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun sendArenaSykmelding(
    producer: MessageProducer,
    session: Session,
    arenaSykmelding: ArenaSykmelding,
    logKeys: String,
    logValues: Array<StructuredArgument>
) = producer.send(session.createTextMessage().apply {
    text = arenaSykmeldingMarshaller.toString(arenaSykmelding)
    log.info("Message is sendt to arena $logKeys", *logValues)
})
