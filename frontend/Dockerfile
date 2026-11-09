# Multi-arch base image: `docker buildx imagetools inspect` showed linux/amd64 + linux/arm64 (2026-11-09).
# Dependencies are installed inside the image so Linux native packages match the container, not the host.
FROM node:24.22.2-alpine3.24
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev"]
