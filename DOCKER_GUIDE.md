# Docker Guide

## Build and Run
```bash
docker-compose up -d --build
```

## Logs
```bash
docker-compose logs -f pdf-converter
```

## Configuration
Configure environment variables in `.env` (see `.env.example`):
- `AI_PROVIDER` (default: `openai`)
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `JAVA_OPTS`

## Stop
```bash
docker-compose down
```
