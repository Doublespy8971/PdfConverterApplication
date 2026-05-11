# AI Summarizer Guide

## Configuration
1. Set the AI provider in `application.properties` or via environment variable:
   ```
   ai.provider=openai
   ```
2. Provide your OpenAI API key:
   ```
   openai.api.key=sk-your-key-here
   openai.model=gpt-3.5-turbo
   ```

Environment variable equivalents:
- `AI_PROVIDER`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`

## Usage
1. Open the UI and select **AI Summarizer**.
2. Upload a PDF and choose summary length.
3. The UI will poll for completion and display the summary.

## API Endpoints
- `POST /api/ai/summarize` → returns `taskId`
- `GET /api/ai/status/{taskId}` → task status
- `GET /api/ai/result/{taskId}` → summary result
