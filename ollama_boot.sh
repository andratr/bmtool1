#!/bin/sh
set -eux

# Start ollama
ollama serve &

# Wait for initialization
until ollama list >/dev/null 2>&1 ; do
  sleep 1
done

# Pull the models you want available
ollama pull nomic-embed-text || true
ollama pull llama3.1:8b      || true
ollama pull llama3.1:70b      || true

# .....
# .....

echo "Ollama models ready."
wait
