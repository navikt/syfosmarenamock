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
    val arenaQueue: String,
    val vaultURL: String,
    val kafkaSm2013AutomaticPapirmottakTopic: String = "privat-syfo-smpapir-automatiskBehandling",
    val kafkaSm2013manuellPapirmottakTopic: String = "privat-syfo-smpapir-manuellBehandling",
    val kafkaSm2013AutomaticDigitalHandlingTopic: String = "privat-syfo-sm2013-automatiskBehandling",
    val kafkaSm2013manuelDigitalManuellTopic: String = "privat-syfo-sm2013-manuelBehandling",
    val kafkaBootstrapServers: String
)
data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)
