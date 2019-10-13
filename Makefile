
all: build deploy

build:
	./gradlew clean jib

deploy:
	kubectl apply -f src/main/k8s/deployment.yaml

undeploy:
	kubectl delete deployment.apps/basketapp-deployment
	kubectl delete ingress/basketapp-ingress
	kubectl delete service/basketapp

