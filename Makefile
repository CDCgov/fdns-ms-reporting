docker-build:
	docker-compose up -d
	printf 'Wait for fdns-ms-combiner\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8085`; do printf '.'; sleep 1; done
	docker build \
	  -t fdns-ms-reporting \
		--network=fdns-ms-reporting_default \
	  --rm \
	  --build-arg REPORTING_PORT=8091 \
	  --build-arg KAFKA_BROKERS=kafka:29092 \
	  --build-arg COMBINER_URL=http://fdns-ms-combiner:8085/ \
	  --build-arg REPORTING_FLUENTD_HOST=fluentd \
	  --build-arg REPORTING_FLUENTD_PORT=24224 \
	  --build-arg OBJECT_URL=http://fdns-ms-object:8083/ \
	  --build-arg REPORTING_PROXY_HOSTNAME= \
	  --build-arg OAUTH2_ACCESS_TOKEN_URI= \
	  --build-arg OAUTH2_PROTECTED_URIS= \
	  --build-arg OAUTH2_CLIENT_ID= \
	  --build-arg OAUTH2_CLIENT_SECRET= \
	  --build-arg SSL_VERIFYING_DISABLE=false \
	  .
	docker-compose down

docker-run: docker-start
docker-start:
	docker-compose up -d
	docker run -d \
		-p 8091:8091 \
		--network=fdns-ms-reporting_default  \
		--name=fdns-ms-reporting_main \
		fdns-ms-reporting

docker-stop:
	docker stop fdns-ms-reporting_main || true
	docker rm fdns-ms-reporting_main || true
	docker-compose down

docker-restart:
	make docker-stop 2>/dev/null || true
	make docker-start

sonarqube:
	docker-compose up -d
	printf 'Wait for fdns-ms-combiner\n'
	until `curl --output /dev/null --silent --head --fail http://localhost:8085`; do printf '.'; sleep 1; done
	docker run -d --name sonarqube -p 9001:9000 -p 9092:9092 sonarqube || true
	mvn clean test sonar:sonar
	docker-compose down
