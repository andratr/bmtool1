


## About The Project

[![Product Name Screen Shot][product-screenshot]](https://example.com)

This tool aims to make it easier to benchmark different code translation AI techniques. It compares different approaches:
        * Prompt engineering
        * Code Chunking
        * Different LLMs
        * LLMs costs

Due to the AI field's rapid evolution, a hexagonal architecture was used, which aims to make it easier to plug more techniques, as well as reach any desired state of scalability.

## Why this exists

See which LLM/provider/model gives the best translation quality for your codebase.

Understand trade-offs (speed, quality, cost).

Keep experiments reproducible by keeping track of your previous tracks.


### Built With

* [![Angular][Angular.io]][Angular-url]
* [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge\&logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
* [![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge\&logo=docker\&logoColor=white)](https://www.docker.com/)
* [![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge\&logo=nginx\&logoColor=white)](https://nginx.org/)
* [![Weaviate](https://img.shields.io/badge/Weaviate-20B2AA?style=for-the-badge)](https://weaviate.io/)
* [![Ollama](https://img.shields.io/badge/Ollama-000000?style=for-the-badge)](https://ollama.com/)


## Getting Started

### Prerequisites

* Docker & Docker Compose


```sh
docker --version
docker compose version
```

### Installation

```sh
# 1) Clone
git clone https://github.com/andratr/bmtool1
cd bmtool1

# 2) Build images
docker compose --profile dev  build
docker compose --profile prod build
```


## Usage



## Runbook (Dev and Prod)

### Dev profile (hot reload)

Start:

```sh

docker compose --profile dev up -d --build backend-dev frontend-dev
```

Logs:

```sh
docker compose --profile dev logs -f backend-dev frontend-dev
```


### Prod profile (Nginx + jar)

Start:

```sh
# stop dev, if running
docker compose --profile dev down

# start prod
docker compose --profile prod up -d --build backend frontend
```

Quick checks:

```sh
# through Nginx with /api
curl -i http://localhost:8085/api/query/ping

# backend-direct (host to container mapping)
curl -i http://localhost:8089/query/ping

# open UI
open http://localhost:8085
```

Restart a single service:

```sh
docker compose --profile prod up -d --build frontend   # changed Angular/nginx.conf
docker compose --profile prod up -d --build backend    # changed backend
```


## Troubleshooting

**HTML “Cannot POST …” from 8085**
Angular dev proxy not in use. Ensure frontend-dev runs with:

```
ng serve --host 0.0.0.0 --port 8085 --proxy-config proxy.compose.dev.json
```

Recreate service:

```sh
docker compose --profile dev up -d --build frontend-dev
```

**CORS in dev**
You’re probably calling full URLs. Use **relative** `/api/...` so the proxy handles it.

**404 on `/api/...` but backend serves `/query/...`**
Rewrites missing. Confirm:

* Dev proxy has `"pathRewrite": { "^/api": "" }`
* Nginx has `rewrite ^/api/(.*)$ /$1 break;`

**Port already allocated**
Bring other profile down:

```sh
docker compose --profile prod down
docker compose --profile dev  down
```

**Dev hot reload sluggish**
Enable IDE auto-make to `target/classes`:

* IntelliJ → Build project automatically ✅
* Advanced Settings → Allow auto-make while app running ✅

## Contributing

Contributions are welcome!

1. Fork the Project
2. Create a Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push (`git push origin feature/AmazingFeature`)
5. Open a PR

## Contact

Andra Trandafir - [andratrandafir@yahoo.com](mailto:andratrandafir@yahoo.com)


## Acknowledgments

* []()
* []()
* []()


