-include .env
export

.PHONY: dev-backend dev-frontend dev-docs build test docker-build rebuild

dev-backend:
	mvn -pl backend spring-boot:run -Dskip.frontend -Dspring-boot.run.profiles=dev,sqlite

dev-frontend:
	cd frontend && npm run dev

dev-docs:
	./scripts/sync-docs.sh
	cd docs-site && npm ci && npm run hugo -- server

build:
	mvn -B -ntp package

test:
	mvn -B -ntp verify

docker-build:
	docker build -f deploy/Dockerfile .

rebuild:
	$(MAKE) build
	$(MAKE) dev-backend
