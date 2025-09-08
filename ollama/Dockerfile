# Dockerfile (for ollama service)
FROM ollama/ollama:latest

# Copy your bootstrap script into the image
COPY ollama_boot.sh /usr/local/bin/ollama_boot.sh

# Ensure it's executable (done inside Linux, so OS-agnostic)
RUN chmod +x /usr/local/bin/ollama_boot.sh

# Run the script on container start
ENTRYPOINT ["/bin/sh", "-euxc", "/usr/local/bin/ollama_boot.sh"]
