# Slack AI Agent

An extensible Slack bot powered by Spring AI and Google Gemini with a convention-based expert system and pluggable skills.

## Adding Your Experts

The easiest way to customize this bot is by adding your own domain experts. **Simply drop documentation files into folders** - the bot will automatically discover and vectorize them.

### Step-by-Step: Add an Expert Using Docker

1. **Create an expert folder** in the `docs/experts/` directory:
   ```bash
   mkdir -p docs/experts/my-product
   ```

2. **Add your documentation files** (Markdown or text):
   ```bash
   # docs/experts/my-product/getting-started.md
   # Getting Started with My Product

   This guide covers installation, configuration, and first steps...

   ## Installation
   Download the latest release from...

   ## Configuration
   Edit the config.yml file...
   ```

   ```bash
   # docs/experts/my-product/api-reference.md
   # API Reference

   ## Authentication
   All API requests require a Bearer token...

   ## Endpoints

   ### GET /api/users
   Returns a list of users...
   ```

3. **Restart the bot** to vectorize your new documentation:
   ```bash
   docker-compose restart bot
   ```

4. **Test it in Slack**:
   ```
   @bot how do I configure My Product?
   @bot what are the API endpoints for My Product?
   ```

   The bot will:
   - Search the vector store for relevant content from your docs
   - Use the LLM to synthesize an answer with source citations
   - Attribute information to specific documentation files

### That's It!

The bot uses a **convention-based discovery system**:
- Any folder under `docs/experts/` becomes an expert domain
- All `.md` and `.txt` files are automatically loaded and vectorized
- The bot uses semantic search to find relevant content when users ask questions
- No code changes required - just drop in your docs and restart

### Volume Mounting (How It Works)

The Docker setup mounts your local `docs/` folder into the container:

```yaml
volumes:
  - ./docs:/app/docs:ro  # Read-only mount
```

This means:
- ✅ Add/edit docs on your host machine
- ✅ Changes are immediately visible to the container
- ✅ Restart the bot to re-vectorize
- ✅ No need to rebuild the Docker image

### Tips for Organizing Experts

```
docs/experts/
├── product-alpha/           # Expert for Product Alpha
│   ├── overview.md
│   ├── installation.md
│   └── troubleshooting.md
├── internal-apis/           # Expert for Internal APIs
│   ├── authentication.md
│   ├── endpoints.md
│   └── rate-limits.md
└── onboarding/              # Expert for Team Onboarding
    ├── first-day.md
    ├── tools.md
    └── culture.md
```

Each folder becomes a separate knowledge domain that the bot can consult.

## Features

- **RAG (Retrieval-Augmented Generation)**: True RAG pattern with context gathering + LLM synthesis
- **Convention-Based Expert System**: Add domain knowledge by dropping docs in folders
- **Extensible Skills**: Add general-purpose capabilities via @Component annotation
- **LLM-Based Skill Routing**: Intelligent skill selection using AI (supports multiple skills per query)
- **Data Access API Integration**: Search Slack conversations, retrieve channel history, and access historical data
- **Intelligent Synthesis**: Final LLM call synthesizes all gathered data with proper attribution
- **Spring AI Integration**: Powered by Google Gemini LLM
- **Vector Search**: ChromaDB for semantic search over documentation
- **Auto-Discovery**: Experts and skills discovered automatically on startup
- **Docker-Ready**: Complete docker-compose setup with Chroma sidecar

## Architecture (RAG Pattern)

```
User Question → Slack
                  ↓
         ┌────────────────────┐
         │   RETRIEVAL PHASE  │
         └────────────────────┘
                  ↓
    ┌─────────────┴─────────────┐
    │                           │
Skill Router              Expert Router
    │                           │
Execute Skills           Vector Search
(Slack messages,         (Documentation)
 APIs, etc.)                    │
    │                           │
Gather raw data          Retrieve docs
    │                           │
    └─────────────┬─────────────┘
                  ↓
         ┌────────────────────┐
         │  GENERATION PHASE  │
         └────────────────────┘
                  ↓
    Final LLM Call with all context
                  ↓
    Synthesized Answer with Attribution
    - Cites sources from docs
    - Notes data from Slack
    - Explicit about assumptions
    - Says "I don't know" when uncertain
```

### Key Components

1. **Skill Router**: LLM determines which skills to execute for data gathering
2. **Expert Router**: LLM determines which documentation to search
3. **Skills**: Gather raw data (Slack conversations, APIs, etc.)
4. **Vector Store**: ChromaDB for semantic search over documentation
5. **Agent Core**: Orchestrates RAG pattern - retrieval then synthesis
6. **Synthesis Layer**: Final LLM call that combines all context into coherent answer
7. **Slack Integration**: Socket mode for real-time responses

### How It Works (RAG Flow)

When you ask **"How to configure the service?"**:

**RETRIEVAL Phase:**
1. **Skill Router** (LLM) determines if any skills are needed
   - Selects `data-access` to search Slack for configuration discussions
2. **Expert Router** (LLM) selects relevant documentation experts
   - Searches vector store for configuration docs
3. **Gather All Context**: Raw Slack messages + documentation chunks

**GENERATION Phase:**
4. **Synthesis**: One final LLM call with all gathered context
   - Gets the original question
   - Gets raw Slack conversation data
   - Gets relevant documentation
   - Synthesizes coherent answer with proper attribution

**What You Get:**
```
According to the Configuration Guide (docs/service-config.md),
the service can be configured via environment variables...

Based on recent Slack discussions in #engineering, the team
recommends using the YAML configuration approach for production...

I don't have specific information about the legacy configuration
method - it's not mentioned in the available documentation.
```

**Benefits:**
- ✅ Proper source attribution (doc references)
- ✅ Clear about data sources (Slack vs docs)
- ✅ Explicit about uncertainty ("I don't know")
- ✅ No hallucination - only uses provided context
- ✅ Coherent synthesis vs raw concatenation

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Google Cloud account with Vertex AI API enabled
- Slack workspace with bot app created

### 1. Configure Slack App

Create a Slack app at https://api.slack.com/apps with:

**OAuth & Permissions → Bot Token Scopes:**
- `app_mentions:read`
- `chat:write`
- `channels:history`
- `groups:history`
- `im:history`
- `mpim:history`
- `search:read` (for Data Access API)

**Event Subscriptions:**
- Enable Events
- Subscribe to `app_mention` event

**App-Level Tokens:**
- Create token with `connections:write` scope

### 2. Set Up Environment

```bash
cp .env.example .env
```

Edit `.env` with your credentials:
```env
GOOGLE_API_KEY=your-google-api-key
GOOGLE_PROJECT_ID=your-google-project-id
GOOGLE_LOCATION=us-central1
SLACK_BOT_TOKEN=xoxb-your-bot-token
SLACK_SIGNING_SECRET=your-signing-secret
SLACK_APP_TOKEN=xapp-your-app-token
```

### 3. Start the Application

```bash
docker-compose up --build
```

This will:
1. Start ChromaDB on port 8000
2. Build and start the bot on port 8080
3. Auto-vectorize all expert documentation
4. Connect to Slack via Socket Mode

### 4. Test in Slack

Invite the bot to a channel and try:
```
@bot what time is it?
@bot how do I use you?
@bot what experts are available?
```

## Expert System Details

For a complete guide on adding experts, see the **[Adding Your Experts](#adding-your-experts)** section at the top of this README.

**Quick recap:**
- Drop docs in `docs/experts/[your-domain]/`
- Restart the bot with `docker-compose restart bot`
- Ask questions in Slack

The bot uses semantic search over vectorized documentation to provide accurate, cited answers.

## Adding Skills

Skills are general-purpose capabilities (weather, calculations, etc.).

Create a class implementing `Skill`:

```java
package com.company.slackagent.skills.builtin;

import com.company.slackagent.skills.*;
import org.springframework.stereotype.Component;

@Component
public class CalculatorSkill implements Skill {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Performs mathematical calculations";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        // Parse and evaluate expression
        String result = calculate(context.getQuery());
        return SkillResult.success("Result: " + result);
    }

    private String calculate(String expression) {
        // Implementation
        return "42";
    }
}
```

Rebuild and restart - skill will be auto-discovered!

## LLM-Based Skill Routing

The agent uses an LLM to intelligently select which skill(s) to use for each query:

### How It Works

1. **Skill Discovery**: On startup, all skills (marked with `@Component`) are automatically registered
2. **Dynamic Selection**: When a query comes in, the `SkillRouter` sends all available skill descriptions to the LLM
3. **Multi-Skill Support**: The LLM can select one or more skills to handle a query
4. **Execution**: All selected skills are executed and their results are combined

### Example

User asks: "What time is it and search for messages about the launch?"

The LLM will route to **both** the `time` and `data-access` skills:
- `time` skill returns current time
- `data-access` skill searches Slack for "launch" messages
- Results are combined and returned to the user

### Benefits

- **No hardcoded routing rules**: Add new skills without modifying routing logic
- **Flexible**: Can use multiple skills for complex queries
- **Intelligent**: LLM understands natural language intent
- **Extensible**: Easy to add new capabilities

## Built-in Skills

### Data Access Skill (`data-access`)

Search and retrieve Slack conversation data:
- Workspace-wide message search
- Channel history retrieval
- Message filtering and formatting

Example queries:
```
@bot search for messages about the deadline
@bot what was discussed in this channel about the project?
@bot show recent messages here
```

### Time Skill (`time`)

Get current time and date information.

Example queries:
```
@bot what time is it?
@bot what's the date?
```

### Echo Skill (`echo`)

Simple echo for testing.

## Project Structure

```
slack-agent/
├── docker-compose.yml          # Docker orchestration
├── Dockerfile                  # Multi-stage build
├── pom.xml                     # Maven dependencies
│
├── src/main/java/com/company/slackagent/
│   ├── SlackAgentApplication.java
│   │
│   ├── config/
│   │   ├── ChromaConfig.java         # Vector store config
│   │   └── SlackConfig.java          # Slack bot config
│   │
│   ├── vectorization/
│   │   ├── StartupVectorization.java  # Auto-vectorize on startup
│   │   ├── DocumentLoader.java        # Load MD/TXT files
│   │   └── DocumentChunker.java       # Chunk documents
│   │
│   ├── experts/
│   │   ├── Expert.java                # Expert model
│   │   ├── ExpertRegistry.java        # Registry of experts
│   │   ├── ExpertRouter.java          # Route to expert
│   │   └── ExpertConsultationService.java  # Query expert
│   │
│   ├── skills/
│   │   ├── Skill.java                 # Skill interface
│   │   ├── SkillRegistry.java         # Auto-discover skills
│   │   ├── SkillRouter.java           # LLM-based skill selection
│   │   └── builtin/
│   │       ├── EchoSkill.java
│   │       ├── TimeSkill.java
│   │       └── DataAccessSkill.java   # Slack Data Access API
│   │
│   ├── agent/
│   │   ├── AgentCore.java             # Main orchestration
│   │   ├── QueryRouter.java           # Route to skill/expert
│   │   └── ResponseFormatter.java     # Format responses
│   │
│   └── slack/
│       └── SlackEventListener.java    # Handle @mentions
│
├── src/main/resources/
│   └── application.yml         # Spring configuration
│
└── docs/experts/
    └── bot/                    # Built-in bot expert
        ├── expert.yml
        ├── usage-guide.md
        ├── available-skills.md
        └── troubleshooting.md
```

## Configuration

### application.yml

Key settings:
```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GOOGLE_PROJECT_ID}
          location: ${GOOGLE_LOCATION}
          chat:
            options:
              model: gemini-2.0-flash-exp
              temperature: 0.7

    vectorstore:
      chroma:
        client:
          host: ${CHROMA_URL:http://localhost:8000}
        collection-name: slack-agent-experts

agent:
  experts:
    base-path: "docs/experts"
    chunk-size: 500
    chunk-overlap: 50
```

## Development

### Local Development (Without Docker)

1. Start ChromaDB:
```bash
docker run -p 8000:8000 chromadb/chroma:latest
```

2. Set environment variables:
```bash
export CHROMA_URL=http://localhost:8000
export GOOGLE_API_KEY=your-key
export SLACK_BOT_TOKEN=your-token
export SLACK_SIGNING_SECRET=your-secret
export SLACK_APP_TOKEN=your-app-token
```

3. Run with Maven:
```bash
mvn spring-boot:run
```

### Logs

View logs:
```bash
docker-compose logs -f bot
docker-compose logs -f chroma
```

### Testing Chroma

```bash
curl http://localhost:8000/api/v1/heartbeat
```

## Troubleshooting

### Bot Not Responding

1. Check containers are running:
```bash
docker-compose ps
```

2. Check bot logs:
```bash
docker-compose logs bot | tail -50
```

3. Verify Slack tokens in `.env`

### Expert Not Found

1. Check expert folder exists: `docs/experts/[name]/`
2. Verify markdown files are present
3. Restart to re-vectorize:
```bash
docker-compose restart bot
```

4. Check logs for vectorization confirmation:
```
✓ Vectorized expert: api (23 chunks from 4 documents)
```

### Slow Responses

- Monitor Gemini API latency
- Check Chroma query performance
- Review chunk size settings in `application.yml`

## Built-in Experts

### Bot Expert

Self-documentation about the bot's capabilities:
- Usage guide
- Available skills
- Troubleshooting

Ask:
```
@bot how do I use you?
@bot what can you do?
@bot help
```

## Roadmap

- [x] LLM-based skill routing (supports multiple skills)
- [x] Data Access API integration
- [ ] Hot reload for documentation changes
- [ ] Multi-expert synthesis
- [ ] Conversation memory
- [ ] Metrics & monitoring
- [ ] Web search skill
- [ ] Admin commands
- [ ] Slack assistant.search.context API integration

## License

MIT

## Contributing

1. Fork the repository
2. Create feature branch from main: `git checkout -b feature/my-feature main`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Submit pull request to main branch

## Support

For issues and questions:
- Check `docs/experts/bot/troubleshooting.md`
- Review logs: `docker-compose logs bot`
- Open an issue on GitHub
