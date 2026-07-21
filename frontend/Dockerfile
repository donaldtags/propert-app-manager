FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S primenest && adduser -S primenest -G primenest

COPY --from=build /app/public ./public
COPY --from=build --chown=primenest:primenest /app/.next/standalone ./
COPY --from=build --chown=primenest:primenest /app/.next/static ./.next/static

USER primenest
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
