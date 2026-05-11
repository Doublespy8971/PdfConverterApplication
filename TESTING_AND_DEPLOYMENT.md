# Testing and Deployment

## Tests
Run the test suite with Maven:
```bash
./mvnw test
```

## Build
```bash
./mvnw clean package
```

## Docker Deployment
```bash
docker-compose up -d --build
```

## Health
After deployment, check:
- `GET http://localhost:8080/api/convert/status/{taskId}`
