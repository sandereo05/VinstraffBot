# Notion Slack-bot

Slack-bot som crawler en Notion-database og svarer i Slack. Java 21, Maven, Slack Bolt (Socket Mode) og Notion REST API via `HttpClient` + Jackson.

> Status: henter og viser rådata. RAG/embeddings er ikke implementert ennå (se `TODO(RAG)` i koden).

## Forutsetninger

- JDK 21+
- Maven 3.9+

## 1. Notion-integrasjon

1. Gå til <https://www.notion.so/my-integrations> og opprett en **Internal integration**.
2. Gi den kun *Read content*-tilgang.
3. Kopier **Internal Integration Secret** → `NOTION_TOKEN`.
4. Åpne databasen i Notion → `•••` → **Connections** → legg til integrasjonen. Uten dette får du `404 object_not_found`.
5. Database-ID-en er 32-tegnsstrengen i URL-en:
   `https://www.notion.so/<workspace>/<DATABASE_ID>?v=...` → `NOTION_DATABASE_ID` (Vinstraffer).
6. Gjør det samme for medlemsdatabasen (Redaksjonsmedlemmer) → `NOTION_MEMBERS_DATABASE_ID`.
   Den må ha en tall-rollup som heter `Ikke innløste vinstraffer`.

## 2. Slack-app

1. Opprett en app på <https://api.slack.com/apps> (**From scratch**).
2. **Socket Mode** → slå på. Lag et App-Level Token med scope `connections:write` → `SLACK_APP_TOKEN` (`xapp-...`).
3. **OAuth & Permissions** → Bot Token Scopes:
   - `commands`
   - `app_mentions:read`
   - `chat:write`
4. **Slash Commands** → opprett `/notion` og `/straffer` (Request URL trengs ikke i Socket Mode).
5. **Event Subscriptions** → slå på og abonner på bot-eventen `app_mention`.
6. **Install App** → installer i workspace. Kopier **Bot User OAuth Token** → `SLACK_BOT_TOKEN` (`xoxb-...`).
7. Inviter boten til kanalen der den skal brukes: `/invite @botnavn`.

## 3. Konfigurasjon

```bash
cp .env.example .env
# fyll inn verdiene
```

Ekte miljøvariabler overstyrer `.env`. Mangler en påkrevd variabel, avslutter appen ved oppstart med en melding som lister hvilke.

| Variabel                   | Påkrevd | Beskrivelse                             |
|----------------------------|---------|-----------------------------------------|
| `NOTION_TOKEN`             | ja      | Notion integration secret               |
| `NOTION_DATABASE_ID`       | ja      | ID til Vinstraffer-databasen            |
| `NOTION_MEMBERS_DATABASE_ID` | ja    | ID til medlemsdatabasen                 |
| `SLACK_BOT_TOKEN`          | ja      | `xoxb-...`                              |
| `SLACK_APP_TOKEN`          | ja      | `xapp-...` (Socket Mode)                |
| `NOTION_CACHE_TTL_MINUTES` | nei     | Cache-levetid for Notion-data, default 5 |
| `LOG_LEVEL`                | nei     | Loggnivå for `com.sander.notionbot`, default `INFO` |

## 4. Kjøring

```bash
mvn package
java -jar target/notion-slack-bot-0.1.0-SNAPSHOT.jar
```

Eller under utvikling:

```bash
mvn compile exec:java -Dexec.mainClass=com.sander.notionbot.App
```

## Bruk

- `/straffer` – viser ikke-innløste vinstraffer per person og anbefalt antall å ta med (halvparten, maks 6). Synlig for hele kanalen.
- `/notion` – viser de 5 nyeste radene (kun synlig for deg).
- `@bot <spørsmål>` – enkel nøkkelordsøk i databasen, svarer i tråd. Erstattes av RAG senere.

## Struktur

```
com.sander.notionbot
├── App                      oppstart, Socket Mode
├── config/Config            env-variabler + validering
├── notion/NotionClient      HTTP mot api.notion.com (paginering, 429-retry)
├── notion/NotionService     konvertering til domenemodell, caching
├── notion/model             NotionPage (record), NotionProperty (sealed interface)
├── slack/SlackApp           registrering av handlers
├── slack/handlers           /notion og @mentions
└── cache/NotionCache        in-memory TTL-cache
```
