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
import no.nav.syfo.util.arenaSykmeldingMarshaller
import no.nav.syfo.util.arenaSykmeldingUnmarshaller
import no.nav.syfo.util.connectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
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
                    val inputQueue = session.createQueue(config.inputQueue)
                    val inputConsumer = session.createConsumer(inputQueue)

                    blockingApplicationLogic(applicationState, inputConsumer)
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

suspend fun blockingApplicationLogic(applicationState: ApplicationState, inputConsumer: MessageConsumer) {
    while (applicationState.running) {
        val message = inputConsumer.receiveNoWait()
        if (message == null) {
            delay(100)
            continue
        }

        try {
            val inputMessageText = when (message) {
                is TextMessage -> message.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }

            val arenaSykmelding = arenaSykmeldingUnmarshaller.unmarshal(StringReader(inputMessageText)) as ArenaSykmelding

            val logValues = arrayOf(
                    StructuredArguments.keyValue("smId", arenaSykmelding.eiaDokumentInfo.dokumentInfo.ediLoggId),
                    StructuredArguments.keyValue("msgId", arenaSykmelding.eiaDokumentInfo.dokumentInfo.dokumentreferanse))

            val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

            log.info("Message is read $logKeys", *logValues)
        } catch (e: Exception) {
            log.error("Exception caught while handling message")
        }

        delay(100)
    }
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
