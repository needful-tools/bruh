# Available Skills

Skills are general-purpose capabilities not tied to domain-specific knowledge.
The bot uses **AI-powered skill routing** to automatically select which skill(s) to use based on your question.

## How Skill Routing Works

1. **Auto-Discovery**: On startup, all skills (marked with `@Component`) are automatically registered
2. **AI Selection**: When you ask a question, an AI model analyzes your query and skill descriptions
3. **Multi-Skill Support**: The AI can select one or more skills to handle complex queries
4. **Smart Execution**: All selected skills execute and their results are combined in the final answer

### Benefits
- ✅ No hardcoded routing rules - just describe what the skill does
- ✅ Can use multiple skills for complex queries
- ✅ Natural language understanding of intent
- ✅ Easy to add new capabilities

## Current Skills

### SlackSearchSkill
**Name**: `slack-search`

Search and retrieve Slack conversation data:
- Workspace-wide message search
- Channel history retrieval
- Message filtering and formatting

**Examples:**
- "@bot search for messages about the deployment"
- "@bot what was discussed in this channel about the project?"
- "@bot show recent messages here"

**When it's used:**
The AI selects this skill when you ask about:
- Finding past conversations
- Searching for specific topics in Slack
- Retrieving channel history
- Looking up what was discussed

### TimeSkill
**Name**: `time`

Returns current date and time information.

**Examples:**
- "@bot what time is it?"
- "@bot what's the date?"

**When it's used:**
The AI selects this skill when you ask about:
- Current time
- Current date
- Time-related information

### EchoSkill
**Name**: `echo`

Echoes back your input (for testing).

**Examples:**
- "@bot echo hello world" → "Echo: hello world"

**When it's used:**
The AI selects this skill when you explicitly ask it to echo something.

## Multi-Skill Queries

The AI can use multiple skills at once!

**Example:**
```
@bot what time is it and search for messages about deadlines
```

The AI will:
1. Route to BOTH `time` and `slack-search` skills
2. Execute both skills in parallel
3. Combine results: current time + messages about deadlines
4. Synthesize a coherent answer

## Adding Custom Skills

Developers can add new skills by:

1. Create a class implementing `Skill` interface
2. Annotate with `@Component`
3. Place in `src/main/java/com/company/slackagent/skills/builtin/`
4. Implement `getName()`, `getDescription()`, and `execute()`

**Example:**
```java
@Component
public class CalculatorSkill implements Skill {
    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Performs mathematical calculations and evaluates expressions";
    }

    @Override
    public SkillResult execute(SkillContext context) {
        // Implementation
        String result = calculate(context.getQuery());
        return SkillResult.success("Result: " + result);
    }

    private String calculate(String expression) {
        // Math evaluation logic
        return "42";
    }
}
```

**Key points:**
- The `description` is crucial - the AI uses it to decide when to use your skill
- Return `SkillResult.success()` with your data
- Return `SkillResult.failure()` if something goes wrong
- No need to modify routing logic - it's automatic!

Rebuild and restart - your skill will be auto-discovered and available!
