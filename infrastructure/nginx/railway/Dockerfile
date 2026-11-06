# CyberConnect DIU edge for Railway (see docs/operations/railway.md). Deploy this directory as the service named
# "edge": the only service with a public domain. Everything else stays on Railway's private network.
FROM nginx:1.28.3-alpine
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY 10-resolver.envsh /docker-entrypoint.d/10-resolver.envsh
RUN chmod +x /docker-entrypoint.d/10-resolver.envsh && rm -f /etc/nginx/conf.d/default.conf
ENV PORT=8080
EXPOSE 8080
