FROM node:20-slim
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY src ./src
ENV NODE_ENV=production
USER node
EXPOSE 8080
CMD ["node", "src/server.js"]
