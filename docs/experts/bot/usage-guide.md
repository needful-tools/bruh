# How to Use This Bot

## Asking Questions

Tag the bot in any channel:
```
@bot search for messages about the deployment
@bot what time is it?
@bot how do I use you?
@bot what experts are available?
```

## Available Skills

The bot has these built-in skills:
- **Data Access**: Search Slack conversations and retrieve channel history
- **Time**: Get current date and time
- **Echo**: Echoes back your query (for testing)

The bot uses AI to automatically select which skill(s) to use based on your question.
It can even use multiple skills at once!

## Available Experts

The bot can consult experts with domain-specific knowledge.
Experts are discovered automatically from the `docs/experts/` folder.

To see current experts, ask: "@bot what experts are available?"

## How It Works (RAG Pattern)

The bot uses a **Retrieval-Augmented Generation (RAG)** pattern:

### RETRIEVAL Phase
1. You ask a question by mentioning @bot
2. **Skill Router** (AI-powered) determines which skills to execute
   - Can select one or multiple skills
   - Skills gather raw data (Slack messages, APIs, etc.)
3. **Expert Router** (AI-powered) determines which documentation to search
   - Searches vector store for relevant documentation
   - Retrieves relevant documentation chunks

### GENERATION Phase
4. **Synthesis**: One final AI call combines all gathered context
   - Takes your original question
   - Includes all data from skills
   - Includes all relevant documentation
   - Generates a coherent answer with proper attribution

### What You Get
- ✅ Answers cite documentation sources
- ✅ Clear about where data comes from (Slack vs docs)
- ✅ Explicit about uncertainty ("I don't know")
- ✅ No hallucination - only uses provided context
- ✅ Coherent synthesis vs raw concatenation

## Examples

**Data access queries:**
- "@bot search for messages about the deployment"
- "@bot what was discussed about the project?"
- "@bot show recent messages in this channel"

**General queries:**
- "@bot what time is it?"
- "@bot echo hello world"

**Bot help:**
- "@bot how do I use you?"
- "@bot what can you do?"

**Domain knowledge queries:**
- Questions about specific domains route to relevant experts
- Bot will cite sources from documentation
- Example: "@bot how do I configure the service?"

**Complex queries (multiple skills):**
- "@bot what time is it and search for messages about deadlines"
- Bot will use both Time and Data Access skills, then synthesize the answer
