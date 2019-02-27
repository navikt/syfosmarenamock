package no.nav.syfo

import java.lang.RuntimeException
import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = env("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = env("APPLICATION_THREADS", "1").toInt(),
    val mqHost: String = env("MQ_HOST"),
    val mqPort: Int = env("MQ_PORT").toInt(),
    val mqQueueManager: String = env("MQ_QUEUE_MANAGER"),
    val mqChannel: String = env("MQ_CHANNEL"),
    val inputQueue: String = env("MQ_INPUT_QUEUE"),
    val smIdXpath: String = env("SMID_XPATH"),
    val stepName: String = env("SM_STEP"),
    val notifyRestMock: Boolean = env("NOTIFY_RESTMOCK", "true").toBoolean()
)

fun env(name: String, default: String? = null) =
        System.getenv(name) ?: default ?: throw RuntimeException("Missing required env variable \"$name\"")

data class VaultCredentials(
    val mqUsername: String,
    val mqPassword: String
)
