# Ollama Setup for AI Vision

Use Ollama when you want to run the vision model locally or on-prem instead of using a hosted AI provider.

Official Ollama documentation:

- Linux install: <https://ollama.com/download/linux>
- Quickstart and model usage: <https://docs.ollama.com/quickstart>
- Docker usage: <https://github.com/ollama/ollama/blob/main/docs/docker.mdx>

## Linux Install

Install Ollama on the machine that will run the model:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Pull a vision-capable model:

```bash
ollama pull llava
```

Ollama uses port `11434` by default. In the AI Vision page, set the base URL to the Ollama host that Ant Media Server can reach:

```text
Base URL: http://<ollama-host>:11434/v1
Token: leave empty
Model: llava
```

Example:

```text
Base URL: http://192.168.1.50:11434/v1
Token: leave empty
Model: llava
```

If Ant Media Server and Ollama run on the same machine without containers, this can also work:

```text
Base URL: http://localhost:11434/v1
Token: leave empty
Model: llava
```

If Ant Media Server or Ollama runs in Docker, avoid assuming `localhost`. Use a reachable host name, Docker service name, host gateway, or IP address.

## Docker Install

Run Ollama with Docker:

```bash
docker run -d \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  --name ollama \
  ollama/ollama
```

Pull the model inside the container:

```bash
docker exec -it ollama ollama pull llava
```

Then set AI Vision to a base URL that Ant Media Server can reach:

```text
Base URL: http://<docker-host>:11434/v1
Token: leave empty
Model: llava
```

For NVIDIA or AMD GPU acceleration, follow the official Docker instructions because the required runtime flags depend on the GPU platform.
