IMAGE_NAME=basketapp
IMAGE_VERSION = 0.1

all: build deploy

build:
	./gradlew clean jib --image $(IMAGE_NAME):$(IMAGE_VERSION)

deploy:
	kubectl apply -f src/main/k8s/deployment.yaml

undeploy:
	kubectl delete deployment.apps/basketapp-deployment
	kubectl delete ingress/basketapp-ingress
	kubectl delete service/basketapp

