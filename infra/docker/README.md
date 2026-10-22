# Container Layout

Service Dockerfiles stay beside their build contexts at `backend/Dockerfile` and `frontend/Dockerfile`; the frontend runtime proxy is in `frontend/infra/nginx.conf`. Root `docker-compose.yml` assembles PostgreSQL, API, and web containers. Shared or deployment-specific container assets belong here when a real cross-service need appears.
