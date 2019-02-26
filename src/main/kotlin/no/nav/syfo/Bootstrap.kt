package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
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
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.util.connectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmarenamock")

fun main() = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val config = ApplicationConfig()
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

                    blockingApplicationLogic(config, applicationState, inputConsumer)
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

suspend fun blockingApplicationLogic(config: ApplicationConfig, applicationState: ApplicationState, inputConsumer: MessageConsumer) {
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

            val smId = inputMessageText.extractXPath(config.smIdXpath)

            log.info("Message is read with {} with {}", keyValue("smId", smId), keyValue("step", config.stepName))
        } catch (e: Exception) {
            log.error("Exception caught while handling message", e)
        }

        delay(100)
    }
}

fun String.extractXPath(xPath: String): String? = XPathFactory.newInstance().newXPath().evaluate(xPath, toXMLDocument())

fun String.toXMLDocument(): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(this)))

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = { applicationState.initialized },
                livenessCheck = { applicationState.running }
        )
    }
}
