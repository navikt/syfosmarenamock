package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = 8080,
    val applicationThreads: Int = 1,
    val mqHost: String,
    val mqPort: Int,
    val mqQueueManager: String,
    val mqChannel: String,
    val inputQueue: String
)
data class VaultCredentials(
    val mqUsername: String,
    val mqPassword: String
)
