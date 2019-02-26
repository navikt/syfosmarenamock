package no.nav.syfo

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

object XPathExtractorSpek : Spek({
    describe("XPathing") {
        it("For Dokumentreferanse") {
            val properties = Properties()
            properties.load(Files.newInputStream(Paths.get("arena_reader_prod.env")))
            """
<?xml version="1.0" encoding="ISO-8859-1"?>
<ArenaSykmelding xmlns="http://www.nav.no/xml/ArenaSykmelding/1/">
  <eia:EiaDokumentInfo xmlns:eia="http://www.nav.no/xml/EiaDokumentInfo/1/">
    <eia:DokumentInfo>
      <eia:DokumentType>SM2</eia:DokumentType>
      <eia:DokumentTypeVersjon>1.0</eia:DokumentTypeVersjon>
      <eia:Dokumentreferanse>Test</eia:Dokumentreferanse>
    </eia:DokumentInfo>
    <eia:AvsenderSystem>
      <eia:SystemNavn>EIA</eia:SystemNavn>
      <eia:SystemVersjon>5.23.2</eia:SystemVersjon>
    </eia:AvsenderSystem>
  </eia:EiaDokumentInfo>
  <ArenaHendelse>
    <Hendelse>
      <HendelsesTypeKode>VEIL_AG_AT</HendelsesTypeKode>
      <MeldingFraLege>Test andre innspill til arbeidsgiver</MeldingFraLege>
      <HendelseStatus>UTFORT</HendelseStatus>
      <HendelseTekst>Sykmelder har gitt veiledning til arbeidsgiver/arbeidstaker (felt 9.1).</HendelseTekst>
    </Hendelse>
  </ArenaHendelse>
  <FoersteFravaersdag>2018-06-05</FoersteFravaersdag>
  <IdentDato>2018-06-05</IdentDato>
</ArenaSykmelding>

            """.trimIndent().extractXPath(properties.getProperty("SMID_XPATH")) shouldEqual "Test"
        }
        it("For infotrygd") {
            val properties = Properties()
            properties.load(Files.newInputStream(Paths.get("infotrygd_reader_prod.env")))
            """
<?xml version="1.0" encoding="ISO-8859-1"?>
<EI_fellesformat xmlns="http://www.trygdeetaten.no/xml/eiff/1/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <MsgHead xmlns="http://www.kith.no/xmlstds/msghead/2006-05-24" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
    <MsgInfo>
      <Type DN="Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding" V="SYKMELD" />
      <MIGversion>v1.2 2006-05-24</MIGversion>
      <GenDate>2018-06-05T11:28:29</GenDate>
      <MsgId>Test</MsgId>
      <Ack DN="Ja" V="J" />
      <Receiver>
        <ComMethod DN="EDI" V="EDI" />
        <Organisation>
          <OrganisationName>NAV IKT</OrganisationName>
        </Organisation>
      </Receiver>
    </MsgInfo>
    <Document>
      <RefDoc>
      </RefDoc>
    </Document>
  </MsgHead>
</EI_fellesformat>

            """.trimIndent().extractXPath(properties.getProperty("SMID_XPATH")) shouldEqual "Test"
        }
    }
})
