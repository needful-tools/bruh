# Troubleshooting

## Bot Not Responding

**Bot was tagged but no response:**
- Verify bot is invited to the channel
- Check Docker containers are running: `docker-compose ps`
- Check bot logs: `docker-compose logs bot`
- Ensure Slack tokens are configured correctly in `.env`
- Verify bot has required Slack permissions (especially `search:read` for Data Access skill)

**Bot responds slowly:**
- Check Gemini API response times (RAG pattern makes multiple AI calls)
- Monitor Chroma query performance
- Review application logs for bottlenecks
- Check if multiple skills are being executed (increases processing time)

## Wrong Answers

**Bot gave incorrect information:**
- Check if expert's documents are up to date
- Restart to re-vectorize: `docker-compose restart bot`
- Review source documents in `docs/experts/[name]/`
- Verify the bot is citing sources correctly (should mention "According to..." or "Based on...")

**Bot routed to wrong expert:**
- Expert descriptions may be unclear
- Add/update `expert.yml` with better description
- Add aliases to help routing
- Expert routing is AI-powered, so ambiguous queries may need refinement

**Bot routed to wrong skill:**
- Skill descriptions may be unclear
- Update the `getDescription()` method in your skill implementation
- Skill routing is AI-powered and learns from skill descriptions
- Try rephrasing your question more specifically

## Skill Issues

**Data Access skill not finding messages:**
- Verify bot has `search:read` permission in Slack
- Check if the bot has access to the channels being searched
- Review Slack API rate limits
- Check logs for API errors

**Skill execution failed:**
- Check bot logs for error messages
- Verify external API credentials (if skill uses external services)
- Ensure skill returns `SkillResult.success()` or `SkillResult.failure()`

## RAG Pattern Issues

**Bot says "I don't have enough information":**
- This is expected behavior when no relevant context is found
- Try being more specific in your question
- Verify relevant documentation exists in `docs/experts/`
- Check if appropriate skills are available for your query

**Bot not citing sources:**
- This may indicate the synthesis phase is not working correctly
- Check logs for AI synthesis errors
- Verify context is being passed to synthesis prompt

## Adding New Experts

**New expert not discovered:**
1. Create folder: `docs/experts/[name]/`
2. Add markdown documents
3. Optionally add `expert.yml` with name, description, and aliases
4. Restart: `docker-compose restart bot`
5. Check logs for vectorization confirmation

**Expected log output:**
```
✓ Vectorized expert: [name] (X chunks from Y documents)
```

## Adding New Skills

**New skill not discovered:**
1. Verify class implements `Skill` interface
2. Ensure class has `@Component` annotation
3. Check class is in `com.company.slackagent.skills` package or subpackage
4. Rebuild and restart: `docker-compose up --build`
5. Check logs for skill registration

**Expected log output:**
```
Registered skill: [skill-name]
```

## Getting Help

**Check logs:**
```bash
docker-compose logs -f bot
docker-compose logs -f chroma
```

**Verify Chroma is running:**
```bash
curl http://localhost:8000/api/v1/heartbeat
```

**Test Slack connectivity:**
- Check bot appears online in Slack
- Try simple query: "@bot what time is it?"
- Check for errors in bot logs

**Debug RAG pattern:**
1. Check retrieval phase: Look for "Executing skill:" and "Retrieved X documents" in logs
2. Check synthesis phase: Look for final AI call in logs
3. Verify context is being gathered correctly
